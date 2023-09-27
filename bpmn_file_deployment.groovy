currentBuild.description = "file deploying in ${target_vm_ip}"

pipeline {
    options{
      timeout(time: 5 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }

    environment {
        TMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='bpmn-file-deployment'
        KUBECONFIG="${TMP_DIR}/config"
        NAMESPACE="bpi"

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
                }
            }

        }

        // stage ("checkout DR-INFRA repo") {
        //     steps{
              
        //      git url: 'https://git.blueplanet.com/Ciena/BPI/infrastructure/dr-infra.git' ,
        //          credentialsId: '6f94116a-b375-47dd-b050-739213759120' ,
        //          branch: '${dr_infra_version}'
        //     }
        // }
        stage("deploy bpmn files in bp2"){
            when{
                beforeAgent true
                expression {
                    return params.envType.equalsIgnoreCase("bp2")
                }
            }
           steps{
           withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook playbooks/cicd/deploy-bpmn-files.yml --vault-password-file $ANSIBLEVAULT \\
                -e bpmn_git_version=${bpmn_git_version} -e target_vm_ip=${target_vm_ip}
                """
           }
            

            }

        }
        stage("deploy bpmn files in k8s"){
            when{
                beforeAgent true
                expression {
                    return params.envType.equalsIgnoreCase("k8s")
                }
            }
           steps{
           withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook playbooks/cicd/deploy-bpmn-files-k8s.yml --vault-password-file $ANSIBLEVAULT \\
                -e bpmn_git_version=${bpmn_git_version} -e target_vm_ip=${target_vm_ip}
                """
           }
            

            }

        }
        stage("restart custartifacts pods"){
            when{
                beforeAgent true
                expression {
                    return params.envType.equalsIgnoreCase("k8s")
                }
            }
           steps{
                script{
                    sh """
                        bash library/shell_scripts/save_kube_context.sh $k8s_provider $clusterName $Region $KUBECONFIG
                        export KUBECONFIG=$KUBECONFIG
                    """
                    sh '''kubectl get pods -n $NAMESPACE | grep -w custartifacts | awk '{print$1}' | xargs kubectl delete pod -n $NAMESPACE'''
                }
            

            }

        }
    }
    post {
        always{
            script{
                echo currentBuild.result
                notify_slack.general_slack_notification(channel_list_slack.channel_bpmn_file_deployment(),currentBuild.result)
            }
        }
        cleanup {
            deleteDir()
        }
    }
 


}