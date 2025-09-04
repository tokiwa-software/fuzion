pipeline {
  agent any
  environment {
    IMAGE_NAME   = "tokiwa-software/fuzion:${env.BRANCH_NAME}"
    DOCS_DEPLOY_HOST  = "fuzion-lang.dev"
    DOCS_DEPLOY_USER  = "deploy_docs"
    DOCS_DEPLOY_DEST  = "/home/deploy_docs/docs_git/"
    SRC_DEPLOY_DEST  = "/home/deploy_docs/src_git/"
    SSH_CRED_ID  = "flangdev-ssh"
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build image') {
      steps { sh 'docker build -t "$IMAGE_NAME" .' }
    }

    stage('Extract docs') {
      steps {
        sh '''
          cid=$(docker create "$IMAGE_NAME")
          mkdir -p docs_out
          docker cp "$cid":/fuzion/apidocs_git/ .
          docker rm "$cid"
        '''
      }
    }

    stage('Deploy docs and src') {
      steps {
        sshagent(credentials: [env.SSH_CRED_ID]) {
          sh '''
            rsync -az --delete --delete-delay --delay-updates \
              ./apidocs_git/ "${DOCS_DEPLOY_USER}@${DOCS_DEPLOY_HOST}:${DOCS_DEPLOY_DEST}"
            rsync -az --delete --delete-delay --delay-updates \
              ./modules/ "${DOCS_DEPLOY_USER}@${DOCS_DEPLOY_HOST}:${SRC_DEPLOY_DEST}"
          '''
        }
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
