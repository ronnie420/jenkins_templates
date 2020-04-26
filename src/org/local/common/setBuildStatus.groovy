package org.mizizi.common

def  setBuildStatus(String message, String state,String git_repo,String context) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: git_repo],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: context],
      errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}
