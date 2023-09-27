currentBuild.description="${FC_BRANCH}" ? "branch_${FC_BRANCH}" : "tag_${FC_TAG}"
def scm_type
def releaseVersnStr
def javaVersion
def jdk_location
pipeline {
    options{
      timeout(time: 5 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }
    agent{
        node {
            label "${jenkins_node}"
        }
    }
    environment{
       // SLACK_CHANNEL=""
        FC_CHECKOUT_DIR="/tmp/${BUILD_TAG}"
        //these jdk paths are in solution maker only
        JDK_7_LOCATION="/opt/jdk7"
        JDK_8_LOCATION="/opt/jdk8"
        JDK_11_LOCATION="/opt/jdk11"
    } 
    tools {
        maven 'maven3.6.3' 
    }
    stages {       
          stage ('load common groovy'){
            steps {
                script{
                    notify_slack = load "${WORKSPACE}/jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "${WORKSPACE}/jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "${WORKSPACE}/jenkins_pipeline/common/utils.groovy" 
                    scm_type=common_func.find_FC_SCM_Type("${release_ver_major}","${release_ver_minor}")
                    releaseVersnStr=common_func.getReleaseVersionStr("${release_ver_major}","${release_ver_minor}")
                    javaVersion=common_func.getJavaVersionForFC("${release_ver_major}","${release_ver_minor}","${release_ver_increment}")
                    jdk_location=getJDKLocation(javaVersion)
                    env.JAVA_HOME=jdk_location
                    echo "SCM type is "+scm_type+" and release version string is "+releaseVersnStr + " java version is "+javaVersion+ " jdk location is "+jdk_location

                }
            }
        }
        stage ('validate parameters'){
            steps {
                script{
                    if ("${FC_BRANCH}" && "${FC_TAG}"){
                        echo "You can not provide FC branch and Tag in same build"
                        currentBuild.result == "FAILURE"
                         sh "exit 1"

                    }
                    if(!releaseVersnStr){
                        echo "calculated release version string is empty"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"

                    }
                    if(!javaVersion || !jdk_location){
                        echo "calculated java version or jdk location is empty"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"

                    }
                }
            }
        }
        
        stage("checkout code"){
        steps{
            dir("$FC_CHECKOUT_DIR/"){
                script{                                      
                    if (scm_type == 'svn') {
                        echo 'found svn'
                        if("${FC_BRANCH}"){
                            repoUrl=''
                            if("${FC_BRANCH}" == 'trunk'){
                                repoUrl="https://svn.infra.bpi.ciena.com/svn/Product/fusion-ui/trunk"
                            }else{
                                repoUrl="https://svn.infra.bpi.ciena.com/svn/Product/fusion-ui/branches/$FC_BRANCH"
                            }
                            checkout([$class: 'SubversionSCM', 
                                    additionalCredentials: [], 
                                    excludedCommitMessages: '', 
                                    excludedRegions: '', 
                                    excludedRevprop: '', 
                                    excludedUsers: '', 
                                    filterChangelog: false, 
                                    ignoreDirPropChanges: false, 
                                    includedRegions: '', 
                                    locations: [[credentialsId: '66113c37-9a2b-4b96-a1e0-6b6c7d64f2cc', 
                                                depthOption: 'infinity', 
                                                //ignoreExternalsOption: true, 
                                                local: '.', 
                                                remote: repoUrl]], 
                                    workspaceUpdater: [$class: 'UpdateUpdater']])

                        }else if("${FC_TAG}"){
                            checkout([$class: 'SubversionSCM', 
                                    additionalCredentials: [], 
                                    excludedCommitMessages: '', 
                                    excludedRegions: '', 
                                    excludedRevprop: '', 
                                    excludedUsers: '', 
                                    filterChangelog: false, 
                                    ignoreDirPropChanges: false, 
                                    includedRegions: '', 
                                    locations: [[credentialsId: '66113c37-9a2b-4b96-a1e0-6b6c7d64f2cc', 
                                                depthOption: 'infinity', 
                                                //ignoreExternalsOption: true, 
                                                local: '.', 
                                                remote: "https://svn.infra.bpi.ciena.com/svn/Product/fusion-ui/tags/$FC_TAG"]], 
                                    workspaceUpdater: [$class: 'UpdateUpdater']])

                        }
                        
    
                    }else if (scm_type=='git'){                         
                        echo 'found git'
                        branch_name=''
                        if("${FC_BRANCH}"){
                           branch_name="${FC_BRANCH}"
                        }else if("${FC_TAG}"){
                            branch_name="${FC_TAG}"
                        }
                        echo "git branch name is: "+branch_name
                        checkout([   $class: 'GitSCM',
                             branches: [[name: branch_name]],
                             extensions: [[$class: 'CloneOption',
                                           depth: 1,
                                           timeout: 30]],
                                    
                             submoduleCfg: [],
                             userRemoteConfigs: [[url: 'https://git.eng.blueplanet.com/software/core-ui/core-ui.git']]
                        ])

                    }
                }
            }
        }
    }
    stage("app server build with java7"){
        tools{
             jdk "jdk7.0.312"
         }
        steps{
            script{
                if(releaseVersnStr==common_func._20_10_x || releaseVersnStr==common_func._21_2_x ){
                    sh '''
                      echo "executing mvn goals for app-server java7 "
                      echo "building with java7 for 20.10 or 21.2.x"
                      cd $FC_CHECKOUT_DIR/app-server/core
                      mvn --version
                      mvn clean deploy -f=blueplanet-build-java6 -Dmaven.test.skip=true -PdefaultProfile,java6              

                    '''
                }else{
                    echo "skipping app-server with java7"
                }

            }

        }

    }
    stage("app-server"){
        steps{
            script{
                if(releaseVersnStr==common_func._20_10_x){
                    echo "inside 20.10.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 20.10.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 20.10.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test

                        '''
                        

                    }


                }
                if(releaseVersnStr==common_func._21_2_x){
                    echo "inside 21.2.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 21.2.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 21.2.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test

                        '''
                        

                    }


                }
                if(releaseVersnStr==common_func._21_6_x){
                    echo "inside 21.6.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 21.6.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release

                        '''

                    }
                   if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 21.6.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test

                        '''
                    

                    }

                }
                if(releaseVersnStr==common_func._21_10_x){
                    echo "inside 21.10.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 21.10.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish-release

                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 21.10.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish

                        '''
                        

                    }

                }
                if(releaseVersnStr==common_func._22_2_x){
                    echo "inside 22.2.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 22.2.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish-release

                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 22.2.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish

                        '''
                        

                    }

                }
                if(releaseVersnStr==common_func._22_8_x){
                    echo "inside 22.8.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 22.8.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish-release

                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 22.8.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish

                        '''
                        

                    }

                }
                     if(releaseVersnStr==common_func._22_12_x){
                    echo "inside 22.12.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 22.12.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish-release

                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 22.12.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish

                        '''
                        

                    }

                }
                if(releaseVersnStr==common_func._23_4_x){
                    echo "inside 23.4.x block"
                    if("${FC_TAG}"){
                        sh '''
                            echo "executing mvn goals for tag in 22.12.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish-release
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test-release
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish-release

                        '''

                    }
                    if("${FC_BRANCH}"){
                        sh '''
                            echo "executing mvn goals for branch in 23.4.x block"
                            cd $FC_CHECKOUT_DIR/app-server/core
                            mvn --version
                            mvn clean deploy -f=blueplanet-build-java8-no-angular -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-config
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-common-web
                            mvn clean deploy -f=blueplanet-core-angular-gui/blueplanet-core-angular-gui-archetype
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pall-publish
                            mvn clean install -f=blueplanet-core-angular-gui/blueplanet-core-angular-showcase/blueplanet-core-showcase-gui -Pnpm-publish-test
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -PdefaultProfile,java8
                            mvn clean deploy -f=blueplanet-core-nodejs-rest -Dmaven.test.skip=true -Ppublish

                        '''
                        

                    }

                }
            }
            
        }

    }
    // stage("bp-installer"){
    //     steps{
    //         sh '''
    //             cd $FC_CHECKOUT_DIR/bp-installer/bp-installer-archtype
    //             mvn --version
    //             mvn clean deploy -Dmaven.test.skip=true
    //         '''
    //     }
    // }
    stage("db-server-neo4j"){
        steps{
            sh '''
              cd $FC_CHECKOUT_DIR/db-server-neo4j/core/
              mvn --version
              mvn clean deploy -Dmaven.test.skip=true

            '''
        }
    }
     stage("db-server-oracle"){
         tools{
             jdk "jdk7.0.312"
         }
        steps{
            sh '''
              cd $FC_CHECKOUT_DIR/db-server-oracle/core
              mvn --version
              mvn clean deploy -Dmaven.test.skip=true

            '''
        }

    }
    stage("db-server-postgres"){
        steps{
            sh '''
              cd $FC_CHECKOUT_DIR/db-server-postgres/core/
              mvn --version
              mvn clean deploy -Dmaven.test.skip=true

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
            sh 'sudo rm -rf $FC_CHECKOUT_DIR'
            deleteDir()
        }
    }
}

def getJDKLocation(java_version){
    load "${WORKSPACE}/jenkins_pipeline/common/utils.groovy" 
    if (java_version==common_func.java8){
        return env.JDK_8_LOCATION
    }else if(java_version==common_func.java11){
        return env.JDK_11_LOCATION
    }
    return ""

}

