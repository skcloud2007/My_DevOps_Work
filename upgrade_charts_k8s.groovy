currentBuild.description="${k8s_provider}-${Region}-${Cluster_name}-${lineup}"
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
       BPHUB_CRED=credentials('a0d75313-34de-419f-987d-3891b5e03196')
       AZURE_CLIENT_SECRET=credentials('0d64dfc7-9d9d-4214-97de-4b8b36312743')
       BITBUCKET_CRED=credentials('b7263acf-b90f-4d5d-9264-bfd0363eb38b')
       KUBECONFIG="${TMP_DIR}/config"


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
    stage("upgrade helm charts"){
        steps{
            sh '''
              mkdir $TMP_DIR
              chmod 700 terraform/bpi/k8s/k8s_upgrade.sh
              bash terraform/bpi/k8s/k8s_upgrade.sh
            '''

        }


    }
    // stage("print loadbalancer services"){
    //     steps{
    //         sh '''
    //           sleep 30
    //           export KUBECONFIG=$KUBECONFIG
    //           kubectl get svc | grep -i loadbalancer 

    //         '''

    //     }

    // }
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


