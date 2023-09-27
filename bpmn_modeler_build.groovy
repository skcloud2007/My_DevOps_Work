
currentBuild.description="${bpmn_modeler_branch}"


pipeline {
    options{
      timeout(time: 5 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
       DR_INFRA_VERSION="master"
       BPMN_MODELER_CHECKOUT_DIR="/tmp/${BUILD_TAG}"
       SLACK_CHANNEL=""

    }
    agent {
        node {
            label "${jenkins_node}"
        }
    }
    tools {
        nodejs "nodejs-12.14.1"
    }
    stages{
       stage("Checkout bpmn modeler branch"){
            steps{
              dir("$BPMN_MODELER_CHECKOUT_DIR/"){
                 git url: 'https://bitbucket.ciena.com/scm/bp_camunda_bpmn/bp-bpmn-modeler.git' ,
                 credentialsId: '6400e9b6-d2c9-4759-aba9-d36ebb9425d7' ,
                 branch: "${bpmn_modeler_branch}"
              }           
            }
       }
       stage("npm build"){
           steps{
             dir("$BPMN_MODELER_CHECKOUT_DIR/"){
               sh 'npm install'
               script{
                   if("${release}".toString().toBoolean()){
                       echo "release is checked"
                       sh 'npm run modeler-release-publish'

                   }else{
                       echo "release is not checked"
                       sh 'npm run modeler-publish'
                    
                   }
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
              sh 'sudo rm -rf $BPMN_MODELER_CHECKOUT_DIR'
              deleteDir()
        }

    }
  
}




