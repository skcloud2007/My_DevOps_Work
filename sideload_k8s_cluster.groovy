currentBuild.description="${k8s_provider}-${Region}-${Cluster_name}"
pipeline {
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }
    agent{
        node {
            label "${jenkins_node}"
        }
    }
    environment{
       // SLACK_CHANNEL=""
       TMP_DIR="/tmp/${BUILD_TAG}"
       KUBECONFIG="${TMP_DIR}/config"
       AZURE_CLIENT_SECRET=credentials('0d64dfc7-9d9d-4214-97de-4b8b36312743')

    } 
    stages {       
        stage ('validate parameters'){
            steps {
                script{
                    if (!"${Cluster_name}" && !"${Region}" && !"${k8s_provider}"){
                        echo "Provide cluster name and region and k8s provider"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                }
            }
        }
    stage("update kubeconfig"){
        steps{
            sh '''
              export KUBECONFIG=$KUBECONFIG
              chmod 700 library/shell_scripts/k8s_loadbalancer.sh
              bash library/shell_scripts/k8s_loadbalancer.sh
            '''
        }

    }
    stage('get loadbalancer'){
        steps{
            script{
                cluster_ip = sh(script:"""
                            kubectl get svc -n bpi | grep -i loadbalancer | awk '{print \$4}' | head -1""",returnStdout:true)
                echo "load balancer for k8s cluster is "+cluster_ip
                newVmIp = cluster_ip.replaceAll("\\s","")
            }
        }
    }
    stage('sideload custartifacts'){
        steps{
            script{
                build job: 'custartifacts-automation', 
                propagate: true, parameters: [string(name: 'product_line', value: "${product_line}"), 
                string(name: 'release_ver_major', value: "${release_ver_major}"), 
                string(name: 'release_ver_minor', value: "${release_ver_minor}"), 
                string(name: 'release_ver_increment', value: "${release_ver_increment}"), 
                string(name: 'release_ver_qualifier', value: "${release_ver_qualifier}"), 
                booleanParam(name: 'flexnet_support', value: "${flexnet_support}"), 
                string(name: 'input_target_vm_ip', value: "${newVmIp}"), 
                string(name: 'input_sideloading_group', value: "${input_sideloading_group}"), 
                string(name: 'dr_infra_branch', value: "${dr_infra_branch}"), 
                string(name: 'environ', value: "${environ}")]
            }
        }
    }
    stage("restart pods"){
        steps{
            script{
                if ("${product_line}"=="bpinventory"){
                    sh '''
                    kubectl get pods -n bpi | grep -w custartifacts | awk '{print$1}' | xargs kubectl delete pod -n bpi
                    '''
                }else if ("${product_line}"=="fusioncore"){
                    sh '''
                    kubectl get pods -n bpi | grep -w fccustartifacts | awk '{print$1}' | xargs kubectl delete pod -n bpi
                    '''
                }
            }
        }
    }
}
    
    post{
        always{
            script{
                echo currentBuild.result
            }
        }
        cleanup {
            sh 'rm -rf $TMP_DIR'
            deleteDir()
        }
    }
}


