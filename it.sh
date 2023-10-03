#!/bin/bash

set -e

case "$OSTYPE" in
  darwin*)
        default="\x1B[0m"
        cyan="\x1B[36m"
        yellow="\x1B[33m"
        magenta="\x1B[35m"
        creeol="\r\033[K"
        ;;
  *)
        default="\e[0m"
        cyan="\e[36m"
        yellow="\e[33m"
        magenta="\e[35m"
        creeol="\r\033[K"
        ;;
esac

fn_echo(){
  echo -en "${creeol}${cyan}$@${default}"
	echo -en "\n"
}

fn_select_group() {
  PS3='Please enter integration test group tag (-Dgroups): '
  options=("anomaly-test" "connection-retry-test" "batch-insert-test" "batch-update-test" "QUIT")
  select opt in "${options[@]}"
  do
      case $opt in
          "connection-retry-test")
              groups=$opt
              break
              ;;
          "batch-insert-test")
              groups=$opt
              break
              ;;
          "batch-update-test")
              groups=$opt
              break
              ;;
          "anomaly-test")
              groups=$opt
              break
              ;;
          "QUIT")
              exit 0
              ;;
          *) echo "invalid option $REPLY";;
      esac
  done
}

fn_select_profile() {
  PS3='Please enter integration test Maven profile (-P): '
  options=("it-local" "it-dedicated" "it-dev" "QUIT")
  select opt in "${options[@]}"
  do
      case $opt in
          "it-local")
              profile=$opt
              fn_select_group
              break
              ;;
          "it-dev")
              profile=$opt
              fn_select_group
              break
              ;;
          "it-dedicated")
              profile=$opt
              fn_select_group
              break
              ;;
          "QUIT")
              exit 0
              ;;
          *) echo "invalid option $REPLY";;
      esac
  done

}

################################
################################
################################

profile=it
groups=

fn_echo "** Integration Test Menu **"

fn_select_profile

fn_echo "Maven Profile: $profile"
fn_echo "Test Group(s): $groups"

./mvnw -P "$profile" -Dgroups="$groups" clean install
