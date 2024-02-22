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

fn_echo_y(){
  echo -en "${creeol}${yellow}$@${default}"
	echo -en "\n"
}

fn_select_group() {
  PS3='Please enter integration test group tag (-Dgroups): '
  options=("all-test" "anomaly-test" "connection-retry-test" "batch-insert-test" "batch-update-test" "batch-rewrite-test" "QUIT")
  select opt in "${options[@]}"
  do
      case $opt in
          "all-test")
                groups=$opt
                break
                ;;
          "anomaly-test")
              groups=$opt
              break
              ;;
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
          "batch-rewrite-test")
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
  options=("it-local" "it-dev" "it-cloud" "QUIT")
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
          "it-cloud")
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

profile=
groups=

fn_select_profile

fn_echo "Maven Profile: $profile"
fn_echo "Test Group(s): $groups"

echo ./mvnw -P "$profile" -Dgroups="$groups" clean install
fn_echo_y "Starting in 5s.."

sleep 5
./mvnw -P "$profile" -Dgroups="$groups" clean install
