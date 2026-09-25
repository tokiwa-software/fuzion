pipeline {
  agent any

  options {
    timeout(time: 30, unit: 'MINUTES')
  }

  environment {
    IMAGE_NAME   = "tokiwa-software/fuzion:${env.BRANCH_NAME}"
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build image') {
      steps { sh 'docker build -t "$IMAGE_NAME" .' }
    }
  }

  post {
    always {
      cleanWs()
    }
    failure {
      script {
        // Send the email using the extracted email
        emailext(
            subject: "Build Failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            body: """
                Build failed. Check the console output:
                ${env.BUILD_URL}
            """,
            recipientProviders: [developers(), requestor()]
        )
      }
    }
  }
}
