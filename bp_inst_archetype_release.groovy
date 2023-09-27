currentBuild.description="${ARCHETYPE_RELEASE_VERSION}" ? "${ARCHETYPE_RELEASE_VERSION}" : "Release from branch ${ARCHETYPE_BRANCH}"
def currentVersion
def versionFrom
def majorVersion
def minorVersion
def incrementVersion
def tagName
def releaseQualifier=""
def release_orchestrator_branch="${cicd_orchestrator_branch}"
def release_orchestrator_event="${cicd_orchestrator_event}"
def allEventName="all"
def new_arch_version
pipeline {
    options{
      timeout(time: 1 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='bp-installer-archetype-devops'
        CURRENT_QUALIFIER="-SNAPSHOT" 
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
                    if(!"${ARCHETYPE_BRANCH}" && !"${ARCHETYPE_RELEASE_VERSION}"){
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
                    if ("${ARCHETYPE_RELEASE_VERSION}"){
                        if("${ARCHETYPE_RELEASE_VERSION}".contains("-")){
                            versn_without_qualifier="${ARCHETYPE_RELEASE_VERSION}".split('\\-')[0]
                            releaseQualifier="-"+"${ARCHETYPE_RELEASE_VERSION}".split('\\-')[1]
                            currentVersion=versn_without_qualifier+"${env.CURRENT_QUALIFIER}"                            
                        }else{
                            currentVersion="${ARCHETYPE_RELEASE_VERSION}"+"${env.CURRENT_QUALIFIER}"
                        }
                                               
                    }
                    if("${ARCHETYPE_BRANCH}"){
                        if(!"${ARCHETYPE_RELEASE_VERSION}"){
                             //calculate from pom file
                                dir("$TEMP_DIR/"){
                                    checkout([   $class: 'GitSCM',
                                        branches: [[name: "${ARCHETYPE_BRANCH}"]],
                                        extensions: [[$class: 'CloneOption',
                                                    depth: 1,
                                                    timeout: 30]],
                                                
                                        submoduleCfg: [],
                                        userRemoteConfigs: [[url: 'https://git.eng.blueplanet.com/software/platform/bpinv-fc-bp-installer.git']]
                            ])
                                pom_content = readMavenPom file: "bp-installer/bp-installer-archtype/pom.xml";
                                currentVersion=pom_content.properties.'blueplanet.bpinstaller.archetype.version'

                            }
                        }                        
                        versionFrom="${ARCHETYPE_BRANCH}"
                    }
                    if(!"${ARCHETYPE_BRANCH}"){
                        //determine branch name from provided release version
                        versions_arr="${ARCHETYPE_RELEASE_VERSION}".split('\\.')
                        versionFrom="v"+versions_arr[0]+"."+versions_arr[1]+".x-SNAPSHOT"
                    }
                    versions_arr="${currentVersion}".split('\\.')
                    majorVersion=versions_arr[0]
                    minorVersion=versions_arr[1]
                    incrementVersion=versions_arr[2].split('\\-')[0]
                    if(releaseQualifier){
                        tagName="v"+majorVersion+"."+minorVersion+"."+incrementVersion+releaseQualifier
                    }else{
                        tagName="v"+majorVersion+"."+minorVersion+"."+incrementVersion
                    }  
                    echo "major version is $majorVersion , minor version is $minorVersion , increment version is $incrementVersion and release qualifier is $releaseQualifier"                  
                    echo "current version of core-ui is $currentVersion , branch name is $versionFrom and tag name is $tagName "
                }
            }
        }
        stage("Create bp installer tag"){
         steps{
             withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook -vvv playbooks/cicd/bp_inst_archetype_release.yml --vault-password-file $ANSIBLEVAULT \\
                -e from_version=$versionFrom -e to_tag_version=$tagName \\
                -e curr_release_ver_major=$majorVersion -e curr_release_ver_minor=$minorVersion \\
                -e curr_release_ver_increment=$incrementVersion -e curr_release_ver_qualifier=${CURRENT_QUALIFIER} \\
                -e target_release_ver_major=$majorVersion -e target_release_ver_minor=$minorVersion \\
                -e target_release_ver_increment=$incrementVersion -e target_release_ver_qualifier=$releaseQualifier \\
                -e bp_inst_create_tag=true

                """
           }
          
            }
        }
        stage("create bp installer from tag"){
            steps{
                script{
                    build(job: 'generate-bpi-installer-from-archetype-git',
                        propagate: true,wait: true, parameters: [[$class: 'StringParameterValue',
                        name: 'gitlabTargetBranch', value: tagName]
                    ])
                }
                // withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                // sh """
                //     export ANSIBLE_FORCE_COLOR=true 
                //     ansible-playbook -vvv playbooks/cicd/bp_inst_archetype_release.yml --vault-password-file $ANSIBLEVAULT \\
                //     -e from_version=$versionFrom -e to_tag_version=$tagName \\
                //     -e curr_release_ver_major=$majorVersion -e curr_release_ver_minor=$minorVersion \\
                //     -e curr_release_ver_increment=$incrementVersion -e curr_release_ver_qualifier=${CURRENT_QUALIFIER} \\
                //     -e target_release_ver_major=$majorVersion -e target_release_ver_minor=$minorVersion \\
                //     -e target_release_ver_increment=$incrementVersion -e target_release_ver_qualifier=$releaseQualifier \\
                //     -e bp_installer_create_archetype_from_tag=true

                //     """
                // }
               
            }

        }
        stage("increment pom version and create PR"){
            when{
                beforeAgent true
                expression {
                    !common_func.is_bp_inst_rc_release("$releaseQualifier")
                }
            }
            steps{
             withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook -vvv playbooks/cicd/bp_inst_archetype_release.yml --vault-password-file $ANSIBLEVAULT \\
                -e from_version=$versionFrom -e to_tag_version=$tagName \\
                -e curr_release_ver_major=$majorVersion -e curr_release_ver_minor=$minorVersion \\
                -e curr_release_ver_increment=$incrementVersion -e curr_release_ver_qualifier=${CURRENT_QUALIFIER} \\
                -e target_release_ver_major=$majorVersion -e target_release_ver_minor=$minorVersion \\
                -e target_release_ver_increment=$incrementVersion -e target_release_ver_qualifier=$releaseQualifier \\
                -e bp_inst_incrmnt_dev_version=true

                """
           }
                
            }

        }
        stage("env"){
            steps{
                script{
                    new_arch_version = "${majorVersion}.${minorVersion}.${incrementVersion}"+"${releaseQualifier}"
                    env.new_arch_version = new_arch_version
                }
            }
        }
        // stage("create bpinstaller from branch"){
        //     when{
        //         beforeAgent true
        //         expression {
        //             !common_func.is_bp_inst_rc_release("$releaseQualifier")
        //         }
        //     }
        //     steps{
        //      withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
        //         sh """
        //         export ANSIBLE_FORCE_COLOR=true 
        //         ansible-playbook -vvv playbooks/cicd/bp_inst_archetype_release.yml --vault-password-file $ANSIBLEVAULT \\
        //         -e from_version=${bpinst_version_from} -e to_tag_version=$tagName \\
        //         -e curr_release_ver_major=$majorVersion -e curr_release_ver_minor=$minorVersion \\
        //         -e curr_release_ver_increment=$incrementVersion -e curr_release_ver_qualifier=${CURRENT_QUALIFIER} \\
        //         -e target_release_ver_major=$majorVersion -e target_release_ver_minor=$minorVersion \\
        //         -e target_release_ver_increment=$incrementVersion -e target_release_ver_qualifier=$releaseQualifier \\
        //         -e bp_installer_create_archetype_from_branch=true

        //         """
        //    }
                
        //     }

        // }
    }
    post {
        always{
            script{
                echo currentBuild.result
                notify_slack.general_slack_notification("${SLACK_CHANNEL_NAME}",currentBuild.result)
                BRANCH_NAME="${release_orchestrator_branch}"
                EVENT_NAME="${release_orchestrator_event}"
                VERSION_PUBLISHED="${majorVersion}.${minorVersion}.${incrementVersion}"
                RELEASE_VERSION="${majorVersion}.${minorVersion}.${incrementVersion}"
                CURRENT_BUILD=env.BUILD_ID
                if ("${release_orchestrator_branch}"){
                    sh(script:"""
                        set -x
                        bash jenkins_pipeline/common/push_events.sh $BRANCH_NAME $EVENT_NAME $VERSION_PUBLISHED $RELEASE_VERSION ${currentBuild.result} $CURRENT_BUILD

                    """)
                }
            }
        }
        cleanup {
            deleteDir()
        }
    }
 


}
