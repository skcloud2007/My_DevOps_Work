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
       VAR_FILE="/tmp/${BUILD_TAG}/${Cluster_name}.tfvars"
       TF_PLAN_FILE="$TMP_DIR/tf_plan"
       AZURE_CLIENT_SECRET=credentials('0d64dfc7-9d9d-4214-97de-4b8b36312743')
       BITBUCKET_CRED=credentials('b7263acf-b90f-4d5d-9264-bfd0363eb38b')
       BPHUB_CRED=credentials('a0d75313-34de-419f-987d-3891b5e03196')
       KUBECONFIG="${TMP_DIR}/config"
       WHITELIST_K8S_IP_AT_LICENSE_SERVER="true"
    } 
    stages {       
          stage ('load common groovy'){
            steps {
                script{
                    notify_slack = load "${WORKSPACE}/jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "${WORKSPACE}/jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "${WORKSPACE}/jenkins_pipeline/common/utils.groovy" 

                }
            }
        }
        stage ('validate parameters'){
            steps {
                script{
                    if (!"${Cluster_name}"){
                        echo "Provide cluster name"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                }
            }
        }
        

    stage("create var file"){
        steps{
            script{
                sh '''
                  mkdir ${TMP_DIR}
                  chmod 700 terraform/bpi/k8s/create_vars_file.sh
                  bash terraform/bpi/k8s/create_vars_file.sh
                '''
            }

        }

    }
    stage("terraform plan"){
        steps{
            sh '''
              cd terraform/bpi/k8s/${k8s_provider}/main
              terraform init -backend-config "key=${k8s_provider}/${Region}/${Cluster_name}.tfstate"
              terraform workspace new ${Cluster_name}
              terraform workspace select ${Cluster_name}
              terraform plan -var-file=$VAR_FILE -out=$TF_PLAN_FILE
            '''
        }
    }
    stage("terraform apply"){
        steps{
           // input(message: 'Click "proceed" to approve the above Terraform Plan')
            sh '''
              cd terraform/bpi/k8s/${k8s_provider}/main 
              #export TF_LOG=DEBUG            
              terraform apply -auto-approve $TF_PLAN_FILE
            '''
        }
    }
    stage("Install helm charts"){
        steps{
            sh '''
              export KUBECONFIG=$KUBECONFIG
              chmod 700 terraform/bpi/k8s/k8s_install.sh
              bash terraform/bpi/k8s/k8s_install.sh
              sleep 60
            '''

        }


    }
    stage("Adding lineup tag"){
        steps{
            sh '''
              if [[ $k8s_provider == 'eks'  ]] 
              then
                bash terraform/bpi/k8s/add_tags_eks.sh
              elif [[ $k8s_provider == 'aks'  ]]
              then
                bash terraform/bpi/k8s/add_tags_aks.sh
              fi
            '''
        }
    }
    stage("print loadbalancer services"){
        steps{
            sh '''
              sleep 60
              export KUBECONFIG=$KUBECONFIG
              echo "charts installed are : "
              helm ls -n bpi | awk '{print $9}' 
              echo "Load balancer URLS are:"
              kubectl get svc | grep -i loadbalancer | awk '{print $1, $4}'   

            '''

        }

    }
}
    
    post{
        always{
            script{
                echo currentBuild.result
               // notify_slack.general_slack_notification("${SLACK_CHANNEL}",currentBuild.result)
            }
        }
        cleanup {
            sh 'sudo rm -rf $TMP_DIR'
            deleteDir()
        }
    }
}


