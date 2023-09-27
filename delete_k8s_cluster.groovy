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
       AZURE_SUBSCRIPTION_ID="4a02d739-9cab-4b6e-9d06-dd2e69af0d84"
       AZURE_CLIENT_ID="046191ac-697b-4b9b-af73-fa6309c03619"
       AZURE_TENANT_ID="457a2b01-0019-42ba-a449-45f99e96b60a"

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
                    if (!"${Cluster_name}" && !"${Region}" && !"${k8s_provider}"){
                        echo "Provide cluster name and region and k8s provider"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                }
            }
        }
    stage("uninstall helm charts"){
        when{
                beforeAgent false
                expression{
                    return params.UNINSTALL_CHARTS
                }
            }
        steps{
            sh '''
              export KUBECONFIG=$KUBECONFIG
              chmod 700 terraform/bpi/k8s/k8s_uninstall.sh
              bash terraform/bpi/k8s/k8s_uninstall.sh
            '''
        }

    }

    stage("check and delete if vnet peering exists"){
        steps{
            sh'''
            if [[ $k8s_provider == 'aks'  ]]
            then
                echo "logging in azure cli"
                az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
                az account set --subscription $AZURE_SUBSCRIPTION_ID
                vnetName=vnet-${Cluster_name}
                resourceGroupName=${Cluster_name}-rg
                peeringName=${Cluster_name}
                peeringList=$(az network vnet peering list --resource-group $resourceGroupName --vnet-name $vnetName --query "[?contains(name, '$peeringName')].[name]" -o tsv)
                if [ -n "$peeringList" ]
                then
                    echo "deleting peering"
                    az network vnet peering delete --resource-group $resourceGroupName --vnet-name $vnetName --name $peeringList
                fi
            fi
            '''
        }
    }
  
    stage("terraform plan destroy "){
        when{
                beforeAgent false
                expression{
                    return params.DESTROY_CLUSTER
                }
            }
        steps{
            sh '''
              cd terraform/bpi/k8s/${k8s_provider}/main
              if [[ $k8s_provider == 'aks' ]]
              then
                export TF_VAR_azure_client_secret=$AZURE_CLIENT_SECRET
              fi  
              terraform init -backend-config "key=${k8s_provider}/${Region}/${Cluster_name}.tfstate"
              terraform workspace select ${Cluster_name}
              terraform plan -destroy -var region=$Region            
              terraform destroy -auto-approve -var region=$Region
              terraform workspace select default
              terraform workspace delete ${Cluster_name}
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
            sh 'rm -rf $TMP_DIR'
            deleteDir()
        }
    }
}


