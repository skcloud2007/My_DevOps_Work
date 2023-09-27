pipeline
{
    options
    {
        timeout(time: 10 , unit: 'HOURS')
        ansiColor('gnome-terminal')

    }
    agent
    {
        node
        {
            label "${jenkins_node}"
        }
    }

    environment 
    {
        AZURE_SUBSCRIPTION_ID="4a02d739-9cab-4b6e-9d06-dd2e69af0d84"
        AZURE_CLIENT_ID="046191ac-697b-4b9b-af73-fa6309c03619"
        AZURE_TENANT_ID="457a2b01-0019-42ba-a449-45f99e96b60a"
        AZURE_CLIENT_SECRET=credentials('0d64dfc7-9d9d-4214-97de-4b8b36312743')
    }

    stages 
    {       
        stage ('load common groovy')
        {
            steps
            {
                script
                {
                    notify_slack = load "${WORKSPACE}/jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "${WORKSPACE}/jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "${WORKSPACE}/jenkins_pipeline/common/utils.groovy" 

                }
            }
        }
        stage ('validate parameters')
        {
            steps 
            {
                script
                {
                    if (!"${Cluster1_name}")
                    {
                        echo "Provide cluster1 name"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                    if (!"${Cluster2_name}")
                    {
                        echo "Provide cluster2 name"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                }
            }
        }
        

        stage("create k8s clusters")
        {
            parallel
            {
                stage('create active site')
                {
                    steps
                    {
                        build job: 'bpi-k8s-installation', parameters: [string(name: 'release_ver_major', value: params.release_ver_major),
                                                                string(name: 'release_ver_minor', value: params.release_ver_minor),
                                                                string(name: 'Cluster_name', value: params.Cluster1_name),
                                                                string(name: 'dr_infra_branch', value: params.dr_infra_branch),
                                                                string(name: 'k8s_provider', value: params.k8s_provider),
                                                                string(name: 'Region', value: params.Cluster1_region),
                                                                string(name: 'Cluster_type', value: params.Cluster_type),
                                                                booleanParam(name: 'is_gr_standby', defaultValue: false),
                                                                extendedChoice(name: 'lineup', value: params.lineup)]
                    }
                    post
                    {
                        success 
                        {
                            sh '''
                            if [[ $k8s_provider == 'aks'  ]]
                            then
                                echo "logging in azure cli"
                                az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
                                az account set --subscription $AZURE_SUBSCRIPTION_ID
                                az aks get-credentials --resource-group ${Cluster1_name}-rg --name ${Cluster1_name}
                            else
                                aws eks update-kubeconfig --region ${Cluster1_region} --name ${Cluster1_name}
                            fi
                            SHOW_LB_IP=$(kubectl -n bpi describe svc https | grep "LoadBalancer Ingress" | cut -d ":" -f2 | sed -e 's/^[[:space:]]*//')
                            echo "Loadbalancer IP: $SHOW_LB_IP"
                            chmod 700 jenkins_pipeline/common/test_gr_pods_health.sh
                            bash jenkins_pipeline/common/test_gr_pods_health.sh
                            echo "done"
                            '''
                        }
                    
                    }

                }
                stage('create standby site')
                {
                    steps
                    {
                        build job: 'bpi-k8s-installation', parameters: [string(name: 'release_ver_major', value: params.release_ver_major),
                                                                        string(name: 'release_ver_minor', value: params.release_ver_minor),
                                                                        string(name: 'Cluster_name', value: params.Cluster2_name),
                                                                        string(name: 'dr_infra_branch', value: params.dr_infra_branch),
                                                                        string(name: 'k8s_provider', value: params.k8s_provider),
                                                                        string(name: 'Region', value: params.Cluster2_region),
                                                                        string(name: 'Cluster_type', value: params.Cluster_type),
                                                                        booleanParam(name: 'is_gr_standby', value: true),
                                                                        extendedChoice(name: 'lineup', value: params.lineup)]
                    }
                }
            }
        }

        stage("create peering between vnets")
        {
            steps
            {
                sh '''
                if [[ $k8s_provider == 'aks'  ]]
                then
                    echo "logging in azure cli"
                    
                    az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
                    az account set --subscription $AZURE_SUBSCRIPTION_ID
                    echo "getting vnetIds"
                    vnet1id=$(az network vnet show --resource-group ${Cluster1_name}-rg --name vnet-${Cluster1_name} --query id --out tsv)
                    vnet2id=$(az network vnet show --resource-group ${Cluster2_name}-rg --name vnet-${Cluster2_name} --query id --out tsv)
                    echo "creating peering"
                    az network vnet peering create --name ${Cluster1_name}-to-${Cluster2_name} --resource-group ${Cluster1_name}-rg --vnet-name vnet-${Cluster1_name} --remote-vnet $vnet2id --allow-vnet-access
                    az network vnet peering create --name ${Cluster2_name}-to-${Cluster1_name} --resource-group ${Cluster2_name}-rg --vnet-name vnet-${Cluster2_name} --remote-vnet $vnet1id --allow-vnet-access
                    echo "peering created"
                fi
                '''
            }
        }

        stage("register standby site")
        {
            steps
            {
                sh '''
                sleep 300
                chmod 700 jenkins_pipeline/common/registerstandbysite.sh
                bash jenkins_pipeline/common/registerstandbysite.sh
                '''
            }
        }

        // stage("standby site nagios healthcheck")
        // {
        //     steps
        //     {
        //         sh '''
        //         if [[ $k8s_provider == 'aks'  ]]
        //         then
        //             echo "logging in azure cli"
        //             az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
        //             az account set --subscription $AZURE_SUBSCRIPTION_ID
        //             az aks get-credentials --resource-group ${Cluster2_name}-rg --name ${Cluster2_name}
        //         else
        //             aws eks update-kubeconfig --region ${Cluster2_region} --name ${Cluster2_name}
        //         fi
        //         SHOW_LB_IP=$(kubectl -n bpi describe svc https | grep "LoadBalancer Ingress" | cut -d ":" -f2 | sed -e 's/^[[:space:]]*//')
        //         echo "Loadbalancer IP: $SHOW_LB_IP"
        //         echo "calling nagios healthcheck"
        //         python3 jenkins_pipeline/common/nagios_check.py --bpHost $SHOW_LB_IP  --retries 500
        //         echo "done"
        //         '''
        //     }
        // }

    }
    
    post
    {
        always
        {
            script
            {
                echo currentBuild.result
            }
        }
    }
}


