currentBuild.description="${FC_RELEASE_VERSION}" ? "${FC_RELEASE_VERSION}" : "Release from branch ${FC_BRANCH}"
def fcCurrentVersion
def fcVersionFrom
def majorVersion
def minorVersion
def incrementVersion
def fcTagName
def releaseQualifier=""
def currentQualifier="-SNAPSHOT"
def release_orchestrator_branch="${cicd_orchestrator_branch}"
def release_orchestrator_event="${cicd_orchestrator_event}"
def allEventName="all"

pipeline {
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='ankit-test-slack-notifications'
        SOLUTION_AUTOMATION_JOB_RESULT='' //placeholder for installation-automation-onlinemode job, stage "Install on a AWS VM"
        //CURRENT_QUALIFIER="-SNAPSHOT"       
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
                }
            }
        }
        stage ("validate parameters"){
            steps{
                script{
                    // if ("${FC_BRANCH}" && "${FC_RELEASE_VERSION}"){
                    //     echo "You can not provide FC branch and version in same build, please specify only one of them"
                    //     currentBuild.result == "FAILURE"
                    //     sh "exit 1"
                    // }
                    if(!"${installer_archetype_version}" && params.coreui_build_archetype==false){
                        echo "Please priovide archetype version or check coreui_build_archetype checkbox."
                        currentBuild.result == "FAILURE"
                        sh "exit 1"                        
                    }else if("${installer_archetype_version}" && params.coreui_build_archetype){
                        echo "Please priovide either archetype version or check coreui_build_archetype checkbox. One cannot do both"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"     
                    }
                    if(!"${FC_BRANCH}" && !"${FC_RELEASE_VERSION}"){
                        echo "Please priovide either branch or version in build."
                        currentBuild.result == "FAILURE"
                        sh "exit 1"                        
                    }
                }
            }
        }
        stage("calculate version"){
            steps{
                script{
                    if ("${FC_RELEASE_VERSION}"){
                        if("${FC_RELEASE_VERSION}".contains("-")){
                            versn_without_qualifier="${FC_RELEASE_VERSION}".split('\\-')[0]
                            releaseQualifier="-"+"${FC_RELEASE_VERSION}".split('\\-')[1]
                            fcCurrentVersion=versn_without_qualifier+currentQualifier                            
                        }else{
                            fcCurrentVersion="${FC_RELEASE_VERSION}"+currentQualifier
                        }
                                               
                    }
                    if("${FC_BRANCH}"){
                        if(!"${FC_RELEASE_VERSION}"){
                             //calculate from pom file
                                dir("$TEMP_DIR/"){
                                    checkout([   $class: 'GitSCM',
                                        branches: [[name: "${FC_BRANCH}"]],
                                        extensions: [[$class: 'CloneOption',
                                                    depth: 1,
                                                    timeout: 30]],
                                                
                                        submoduleCfg: [],
                                        userRemoteConfigs: [[url: 'https://git.eng.blueplanet.com/software/core-ui/core-ui.git']]
                            ])
                                pom_content = readMavenPom file: "app-server/core/blueplanet-build-java8-no-angular/pom.xml";
                                fcCurrentVersion=pom_content.parent.properties.'version'

                            }
                        }                        
                        fcVersionFrom="${FC_BRANCH}"
                    }
                    if(!"${FC_BRANCH}"){
                        //determine branch name from provided release version
                        versions_arr="${FC_RELEASE_VERSION}".split('\\.')
                        fcVersionFrom="maintenance/v"+versions_arr[0]+"."+versions_arr[1]+".x-SNAPSHOT"
                    }
                    versions_arr="${fcCurrentVersion}".split('\\.')
                    majorVersion=versions_arr[0]
                    minorVersion=versions_arr[1]
                    incrementVersion=versions_arr[2].split('\\-')[0]
                    currentQualifier="-"+"${fcCurrentVersion}".split('\\-')[1]
                    if(releaseQualifier){
                        fcTagName="v"+majorVersion+"."+minorVersion+"."+incrementVersion+releaseQualifier
                    }else{
                        fcTagName="v"+majorVersion+"."+minorVersion+"."+incrementVersion
                    }  
                    if(params.DEV_BUILD){
                        fcTagName=fcVersionFrom
                        releaseQualifier=currentQualifier
                    }
                    echo "major version is $majorVersion , minor version is $minorVersion , increment version is $incrementVersion and release qualifier is $releaseQualifier and current qualifier is $currentQualifier"                  
                    echo "current version of core-ui is $fcCurrentVersion , branch name is $fcVersionFrom and tag name is $fcTagName "
                }
            }
        }
        stage("Create FC Tag"){
            when{
                beforeAgent true
                expression {
                    if( !params.DEV_BUILD && ("${release_orchestrator_event}"== "coreui_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}")){
                        return true
                    }else{
                        return false
                    }
                }
            }
         steps{
           script{
                    build(job: 'FC_release_create_tag_v1.1',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'fc_version_from', value: fcVersionFrom],
                        [$class: 'StringParameterValue',
                        name: 'fc_tag_version_to', value: fcTagName],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"],
                        [$class: 'StringParameterValue',
                        name: 'curr_release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'curr_release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'curr_release_ver_increment', value: incrementVersion],
                        [$class: 'StringParameterValue',
                        name: 'curr_release_ver_qualifier', value: currentQualifier],
                        [$class: 'StringParameterValue',
                        name: 'target_release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'target_release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'target_release_ver_increment', value: incrementVersion],
                        [$class: 'StringParameterValue',
                        name: 'target_release_ver_qualifier', value: releaseQualifier]
                    ])
                }
            }
        }
        stage("SDK Release"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    build(job: 'FC_release_sdk_v1.1',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'FC_TAG', value: params.DEV_BUILD ? "" : fcTagName],
                        [$class: 'StringParameterValue',
                        name: 'FC_BRANCH', value: params.DEV_BUILD ? fcTagName : ""],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion]

                    ])
                }
            }

        }
         stage("fc-bpinst"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    scm_type=common_func.find_FC_SCM_Type(majorVersion,minorVersion)
                    bpinst_build_job="build-fc-bpinst-branch-"+majorVersion+"."+minorVersion+".x"
                    if (scm_type == 'git'){
                        build(job: "${bpinst_build_job}",
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'git.branch', value: fcTagName]
                    ])

                    }
                   
                }
            }

        }
        stage("build archetype"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    if(!"${installer_archetype_version}" && params.coreui_build_archetype){
                        def b = build job: 'bp_installer_archetype_release_pipeline', parameters: [ string(name: 'ARCHETYPE_RELEASE_VERSION', value: "${ARCHETYPE_RELEASE_VERSION}"), string(name: 'ARCHETYPE_BRANCH', value: "${ARCHETYPE_BRANCH}"), string(name: 'dr_infra_version', value: "${dr_infra_version}"), string(name: 'jenkins_node', value: "${jenkins_node}")]
                        env.new_arch_version = b.getBuildVariables()["new_arch_version"]
                        installer_archetype_version = env.new_arch_version
                    }
                }
            }
        }
        stage("Create solution"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_test_sol" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    build(job: 'solution-automation',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'product_line', value: "fusioncore"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_qualifier', value: releaseQualifier],
                        [$class: 'StringParameterValue',
                        name: 'installer_archetype_version', value: "${installer_archetype_version}"],
                        [$class: 'BooleanParameterValue',
                        name: 'allow_open_neo4j_port', value: "${allow_open_neo4j_port}"],
                        [$class: 'StringParameterValue',
                        name: 'var_lineup_xp_solverbase', value: "${var_lineup_xp_solverbase}"],
                        [$class: 'StringParameterValue',
                        name: 'testing_prefix', value: "${testing_prefix}"],
                        [$class: 'BooleanParameterValue',
                        name: 'download_archetype', value: true ],
                        [$class: 'BooleanParameterValue',
                        name: 'build_flexnet', value: "${build_flexnet}"],
                        [$class: 'StringParameterValue',
                        name: 'lib_sol_chart_ver', value: "${lib_sol_chart_ver}"],
                        [$class: 'BooleanParameterValue',
                        name: 'publish_bp2', value: "${publish_bp2}"],
                        [$class: 'BooleanParameterValue',
                        name: 'publish_helm', value: "${publish_helm}"]
                    ])

                }
            }

        }
        stage("Install on a AWS VM"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_regression_test" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    autovm_comments="release-"+fcTagName
                    SOLUTION_AUTOMATION_JOB_RESULT=build(job: 'installation-automation-onlinemode',
                            propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'env_owner', value: "Alexey,Artem"],
                            [$class: 'StringParameterValue',
                            name: 'product_line', value: "fusioncore"],
                            [$class: 'StringParameterValue',
                            name: 'autovm_comments', value: autovm_comments],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_major', value: majorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_minor', value: minorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_increment', value: incrementVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_qualifier', value: releaseQualifier],
                            [$class: 'StringParameterValue',
                            name: 'var_lineup_xp_solverbase', value: "${var_lineup_xp_solverbase}"],
                            [$class: 'StringParameterValue',
                            name: 'testing_prefix', value: "${testing_prefix}"],
                            [$class: 'StringParameterValue',
                            name: 'aws_vm_region', value: "${aws_region}"],
                            [$class: 'StringParameterValue',
                            name: 'target_vm_subnet', value: "${aws_subnet}"],
                            [$class: 'StringParameterValue',
                            name: 'BP2_solutions', value: "${BP2_solutions}"]
                    ])

                }
            }

        }
        stage("Sanity Test"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_regression_test" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
               sh 'sleep 15m'
               script{
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE'){
                    //find IP of launched VM in previous stage
                    new_vm_ip = sh(script:"""
                                set -x
                                aws ec2 --region ${aws_region} describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${SOLUTION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{ip:PrivateIpAddress}\" --output json | jq -r '.[0][0].ip' """,returnStdout:true)
                        //common_func= load "jenkins_pipeline/common/utils.groovy"
                        scm_type=common_func.find_FC_SCM_Type(majorVersion,minorVersion)
                        sanityJobName=getCoreUISanityJobName(majorVersion,minorVersion)
                        if (scm_type=='git') {
                            //sanity_repo="https://svn.infra.bpi.ciena.com/svn/Product/fusion-ui/tags/" + "$fcTagName/app-server/core"
                            build(job: sanityJobName,
                                propagate: true, parameters: [[$class: 'StringParameterValue',
                                name: 'test.env.hostname', value: "${new_vm_ip}"],
                                [$class: 'StringParameterValue',
                                name: 'git.branch', value: fcTagName]
                        ])

                        }    
                }     


               }
                
                
            }

        }
        stage("Repack solution"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "coreui_publish_sol_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
           steps{
              script{
                if((currentBuild.currentResult=='UNSTABLE' || currentBuild.currentResult=='SUCCESS')){
                    build(job: 'util-repacksolution',
                            propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'product_line', value: "fusioncore"],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_major', value: majorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_minor', value: minorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_increment', value: incrementVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_qualifier', value: releaseQualifier],
                            [$class: 'StringParameterValue',
                            name: 'prefix_toremove', value: "${testing_prefix}"]
                        ])
                }else{
                    sh 'exit 1'
                }
              }
           }
        }
        stage("Post release tasks"){
            when{
                beforeAgent true
                expression {
                    if( !params.DEV_BUILD &&   ( !common_func.is_fc_rc_release(releaseQualifier) && ("${release_orchestrator_event}"== "coreui_publish_sol_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"))){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                echo "update pom version in branch"
                withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                    sh """
                    export ANSIBLE_FORCE_COLOR=true 
                    ansible-playbook -vvv playbooks/cicd/fc_release.yml --vault-password-file $ANSIBLEVAULT \\
                    -e from_version=$fcVersionFrom -e to_tag_version=$fcTagName \\
                    -e curr_release_ver_major=$majorVersion -e curr_release_ver_minor=$minorVersion \\
                    -e curr_release_ver_increment=$incrementVersion -e curr_release_ver_qualifier=$currentQualifier \\
                    -e target_release_ver_major=$majorVersion -e target_release_ver_minor=$minorVersion \\
                    -e target_release_ver_increment=$incrementVersion -e target_release_ver_qualifier=$releaseQualifier \\
                    -e core_ui_post_release=true

                    """
           }
               echo "create sdk after updating pom version in branch"
                script{
                    build(job: 'FC_release_sdk_v1.1',
                        propagate: false,wait: false, parameters: [[$class: 'StringParameterValue',
                        name: 'FC_BRANCH', value: fcVersionFrom],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion]
                    ])

                    if (fcVersionFrom == 'main') {
                        echo "running on created maintenance branch"
                        maintenance_branch="maintenance/v"+majorVersion+"."+minorVersion+".x-SNAPSHOT"
                        build(job: 'FC_release_sdk_v1.1',
                        propagate: false,wait: false, parameters: [[$class: 'StringParameterValue',
                        name: 'FC_BRANCH', value: maintenance_branch],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion]
                    ])
                        

                    }

                }
                
            }

        }
        stage("Archetype Version used"){
            steps{
                echo "${installer_archetype_version}"
            }
        }
    }
    post {
        always{
            script{
                if (currentBuild.result == 'SUCCESS' || currentBuild.result == 'UNSTABLE'){
                    final_result = 'SUCCESS'
                }
                else{
                    final_result = currentBuild.result
                }
                echo final_result
                // echo currentBuild.result
                notify_slack.general_slack_notification("${SLACK_CHANNEL_NAME}",currentBuild.result)
                BRANCH_NAME="${release_orchestrator_branch}"
                EVENT_NAME="${release_orchestrator_event}"
                VERSION_PUBLISHED="${majorVersion}.${minorVersion}.${incrementVersion}"
                RELEASE_VERSION="${majorVersion}.${minorVersion}.${incrementVersion}"
                CURRENT_BUILD=env.BUILD_ID
                if ("${release_orchestrator_branch}"){
                    sh(script:"""
                        set -x
                        bash jenkins_pipeline/common/push_events.sh $BRANCH_NAME $EVENT_NAME $VERSION_PUBLISHED $RELEASE_VERSION ${final_result} $CURRENT_BUILD

                    """)
                }
            }
        }
        // success{
        // //if success delete launched VM
        //  script{
        //     instance_id = sh(script:"""
        //                         set -x
        //                         aws ec2 --region ${aws_region} describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${SOLUTION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{id:InstanceId}\" --output json | jq -r '.[0][0].id' """,returnStdout:true)
        //                         if ("${instance_id}" ){
        //                             sh(script: "aws ec2 --region ${aws_region} terminate-instances --instance-ids ${instance_id}")
        //                         }
                          

        //     }
        // }
        cleanup {
            deleteDir()
        }
    }
}


def getCoreUISanityJobName(majorVersn,minorVersn){
    if(majorVersn.toInteger()>=23 ){
        return "Core UI solution Sanity testing from 2212"
    }else if(majorVersn.toInteger()==22 && minorVersn.toInteger()==12){
        return "Core UI solution Sanity testing from 2212"
    }else{
        return "Core UI solution Sanity testing"
    }
    return ""

}

