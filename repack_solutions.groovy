currentBuild.description="${release_ver_major}.${release_ver_minor}.${release_ver_increment}-${release_ver_qualifier}, product=${product_line},soltype=${SOLUTION_TYPE}"

pipeline{
    options{
      timeout(time: 1 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='bpi-installer-automation-notification'    
    }
    agent{
        node {
            label "${jenkins_node}"
        }
    }
    stages{
        stage ('load common groovy'){
            steps {
                script{
                    notify_slack = load "jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "jenkins_pipeline/common/utils.groovy" 
                    email= load "jenkins_pipeline/common/send_email.groovy" 
                }
            }
        }
        stage ("validate parameters"){
            steps{
                script{                    
                    if ("${product_line}".trim().length()==0){
                        echo "select product to build"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"  
                    }                 
                    if(!"${release_ver_major}" && !"${release_ver_minor}" && !"${release_ver_increment}"){
                        echo "Please provide versions"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"                        
                    }                    
                }
            }
        }
        stage("repack bp2 solution"){
            when{
                beforeAgent true
                expression {
                    return params.SOLUTION_TYPE.contains("BP2")
                }
            }
            steps{
                script{
                    withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                            sh """
                            export ANSIBLE_FORCE_COLOR=true 
                            ansible-playbook playbooks/cicd/pb-bpinstaller-6-repacksolution.yml --vault-password-file $ANSIBLEVAULT \\
                            -e release_ver_major=${release_ver_major} -e release_ver_minor=${release_ver_minor} -e release_ver_increment=${release_ver_increment} \\
                            -e release_ver_qualifier=${release_ver_qualifier} \\
                            -e prefix_toremove=${prefix_toremove} -e product_line=${product_line} -e BP2_solutions='${BP2_solutions}'

                            """
                        }
                }
            }

        }
        stage("repack helm solutions"){
            when{
                beforeAgent true
                expression {
                    return params.SOLUTION_TYPE.contains("K8s")
                }
            }
             steps{
                script{
                    fullVersion=""
                    if (!"${release_ver_qualifier}"){
                        fullVersion="${release_ver_major}.${release_ver_minor}.${release_ver_increment}"
                    }else{
                        fullVersion="${release_ver_major}.${release_ver_minor}.${release_ver_increment}${release_ver_qualifier}"
                    }
                    sh """
                      export PYTHONUNBUFFERED=true
                      python3 library/py/helm_solutions_repack/repack_helm_solution_main.py -product="${product_line}" -version=$fullVersion -testKey="${prefix_toremove}" -solutionToRepack='$K8s_solutions'

                    """
                    
                }
            }

        }
        stage("upload version repacked in s3"){
            when{
                beforeAgent true
                expression{
                    return params.publish_versn_detail_at_s3_prefix.trim().length()>0
                }
            }
            steps{
                sh """
                    export PYTHONUNBUFFERED=true
                    python3 library/py/publish_sol_detail__in_s3/publish_repacked_version_detail_in_s3.py --productLine="${product_line}" --testKey="${prefix_toremove}" --bp2Version='$BP2_solutions' --k8sVersion='$K8s_solutions' --s3Prefix='$publish_versn_detail_at_s3_prefix'

                """
            }
        }
        
    }
    post{
        always{
            script{
                echo currentBuild.result
            }
        }
        failure{
            script{
                email.send_email_general()
            }
        }
        cleanup {
            deleteDir()
        }
    }
}
