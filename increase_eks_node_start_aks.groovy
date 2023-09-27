currentBuild.description="Starting AKS and increasing EKS nodes"
pipeline {
    options{
      timeout(time: 20 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }
    agent{
        node {
            label "solution-maker"
        }
    }
    environment{
        AZURE_CLIENT_ID='046191ac-697b-4b9b-af73-fa6309c03619'
        AZURE_CLIENT_SECRET='_568Q~-ynmrbMOEreEuhRg-eDLeNOBPc4afAuaNW'
        AZURE_SUBSCRIPTION_ID='4a02d739-9cab-4b6e-9d06-dd2e69af0d84'
        AZURE_TENANT_ID='457a2b01-0019-42ba-a449-45f99e96b60a'
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
    
    stage("Starting AKS and checking Nagios"){
        steps{
            script{
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE'){
                    sh '''
                        started_aks_clusters=()
                        rg_started_aks_clusters=()
                        az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
                        az aks list > kub_rg.json
                        kub=($(jq -r '.[].agentPoolProfiles[0].tags.Name' kub_rg.json))
                        echo ${kub[@]}
                        rg=($(jq -r '.[].resourceGroup' kub_rg.json))
                        echo ${rg[@]}
                        kub_stop=($(jq -r '.[].tags.weekend_stop' kub_rg.json))
                        echo ${kub_stop[@]}
                        for i in $(seq 0 $(( ${#kub[@]} - 1 )));
                        do
                        if [ "${kub_stop[$i]}" == "yes" ]; then
                            started_aks_clusters+=("${kub[$i]}")
                            rg_started_aks_clusters+=("${rg[$i]}")
                            az aks start --name ${kub[$i]} --resource-group ${rg[$i]}
                        fi
                        done
                        sleep 180
                        az account set --subscription $AZURE_SUBSCRIPTION_ID
                        for j in $(seq 0 $(( ${#started_aks_clusters[@]} - 1 )));
                        do
                            az aks get-credentials --resource-group ${rg_started_aks_clusters[$j]} --name ${started_aks_clusters[$j]}
                            res=$( kubectl get svc --namespace bpi | grep https )
                            read -a ext <<< $res
                            newVmIp=${ext[3]}
                            python3 jenkins_pipeline/common/nagios_check.py --bpHost $newVmIp --retries 300
                        done
                    '''
                }
            }
        }
    }
    stage("Increasing EKS Node count and checking Nagios"){
        steps{
            script{
                sh '''
                    increased_eks_name_mum=()
                    region_mum="ap-south-1"
                    aws eks list-clusters --region $region_mum > cluster.json
                    cluster_list=($( jq -r '.clusters' cluster.json | sed 's/[][]//g' | tr ',' ' ' | sed 's/\"//g' ))
                    echo "${cluster_list[@]}"
                    for cluster in "${cluster_list[@]}";
                    do
                        val=$( aws eks describe-cluster --name "$cluster" --region "$region_mum" | jq -r '.cluster.tags.weekend_stop' )
                        if [ "$val" == "yes" ]; then
                            increased_eks_name_mum+=("$cluster")
                            aws eks list-nodegroups --cluster-name "$cluster" --region "$region_mum" > ng.json
                            nodegroups=($( jq -r '.nodegroups' ng.json | sed 's/[][]//g' | tr ',' ' ' | sed 's/\"//g' ))
                            for ng in "${nodegroups[@]}";
                            do
                                aws eks update-nodegroup-config --cluster-name "$cluster" --nodegroup-name "$ng" --region "$region_mum" --scaling-config '{"minSize":2,"maxSize":2,"desiredSize":2}'
                            done
                            rm ng.json
                        fi
                    done
                    rm cluster.json
                    increased_eks_name_us=()
                    region_us="us-east-1"
                    aws eks list-clusters --region $region_us > cluster.json
                    cluster_list=($( jq -r '.clusters' cluster.json | sed 's/[][]//g' | tr ',' ' ' | sed 's/\"//g' ))
                    echo "${cluster_list[@]}"
                    for cluster in "${cluster_list[@]}";
                    do
                        val=$( aws eks describe-cluster --name "$cluster" --region "$region_us" | jq -r '.cluster.tags.weekend_stop' )
                        if [ "$val" == "yes" ]; then
                            increased_eks_name_us+=("$cluster")
                            aws eks list-nodegroups --cluster-name "$cluster" --region "$region_us" > ng.json
                            nodegroups=($( jq -r '.nodegroups' ng.json | sed 's/[][]//g' | tr ',' ' ' | sed 's/\"//g' ))
                            for ng in "${nodegroups[@]}";
                            do
                                aws eks update-nodegroup-config --cluster-name "$cluster" --nodegroup-name "$ng" --region "$region_us" --scaling-config '{"minSize":2,"maxSize":2,"desiredSize":2}'
                            done
                            rm ng.json
                        fi
                    done
                    echo "Updated clusters in ap-south-1 are ${increased_eks_name_mum[@]}"
                    echo "Updated clusters in us-east-1 are ${increased_eks_name_us[@]}"
                    sleep 180 
                    for name_mum in "${increased_eks_name_mum[@]}";
                    do
                        aws eks --region ap-south-1 update-kubeconfig --name "$name_mum"
                        res=$( kubectl get svc --namespace bpi | grep https )
                        read -a ext <<< $res
                        newVmIp=${ext[3]}
                        python3 jenkins_pipeline/common/nagios_check.py --bpHost $newVmIp --retries 300
                    done

                    for name_us in "${increased_eks_name_us[@]}";
                    do
                        aws eks --region us-east-1 update-kubeconfig --name "$name_us"
                        res=$( kubectl get svc --namespace bpi | grep https )
                        read -a ext <<< $res
                        newVmIp=${ext[3]}
                        python3 jenkins_pipeline/common/nagios_check.py --bpHost $newVmIp --retries 300
                    done
                '''
            }
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
