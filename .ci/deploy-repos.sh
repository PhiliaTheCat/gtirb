#!/bin/bash
#
# deploy-repos.sh
#
# Usage:
#   GTIRB_BRANCH=release-1.10.3 GTIRB_PPRINTER_BRANCH=release-1.6.0 DDISASM_BRANCH=release-1.4.0 ./deploy_repos.sh deploy_directory/
#
# Summary: Creates three apt repositories underneath deploy_directory
#   (for xenial, bionic, and focal).  The created directory structure is:
#     - deploy_directory
#       - xenial
#         - [ubuntu16 packages]
#         - Packages.gz
#       - bionic
#         - [ubuntu18 packages]
#         - Packages.gz
#
#   deploy_directory gets completely whiped out and recreated by this script,
#   so be careful.
#
#   In order to install packages from the created repositories, one would add
#   one of the above directories to their /etc/apt/sources.list file (see the
#   'Build' section of README.md).

set -o xtrace
set -o nounset
set -o errexit
set -o pipefail

DEPLOY_DIR=$1

GTIRB_BRANCH=${GTIRB_BRANCH:?Please specify the GTIRB_BRANCH}
GTIRB_PPRINTER_BRANCH=${GTIRB_PPRINTER_BRANCH:?Please specify the GTIRB_PPRINTER_BRANCH}
DDISASM_BRANCH=${DDISASM_BRANCH:?Please specify the DDISASM_BRANCH}

rm -rf $DEPLOY_DIR
mkdir -p $DEPLOY_DIR

function setup_repo {
  local OS=$1
  local CODENAME=$2
  mkdir $DEPLOY_DIR/$CODENAME
  pushd $DEPLOY_DIR/$CODENAME
  case $OS in
    ubuntu16) JOB_NAME='debian-installer-ubuntu16' ;;
    ubuntu18) JOB_NAME='debian-installer-ubuntu18' ;;
    ubuntu20) JOB_NAME='debian-installer-ubuntu20' ;;
    arch) JOB_NAME='package-arch' ;;
    windows-debug) JOB_NAME='build-windows-msvc-debug';;
    windows-release) JOB_NAME='build-windows-msvc-relwithdebinfo';;
  esac
  for PROJECT in gtirb gtirb-pprinter ddisasm;do
    VAR_NAME="${PROJECT^^}_BRANCH"
    VAR_NAME="${VAR_NAME/-/_}"
    BRANCH=${!VAR_NAME}
    curl -L https://git.grammatech.com/rewriting/${PROJECT}/-/jobs/artifacts/$BRANCH/download?job=${JOB_NAME} \
         --output "${PROJECT}-artifacts.zip"
    case $OS in
      arch|ubuntu16|ubuntu18|ubuntu20)
        unzip -o ${PROJECT}-artifacts.zip
        rm *.zip
        ;;
      windows*)
        mkdir -p /tmp/pdb/
        unzip -o ${PROJECT}-artifacts.zip \*.pdb -d /tmp/pdb/
        zip -d ${PROJECT}-artifacts.zip \*.pdb
        ;;
    esac
  done
  case $OS in
    ubuntu16|ubuntu18|ubuntu20)
      curl http://otsego.grammatech.com/u4/TARBALLS/debloat/pkgs/libcapstone-dev_4.0.1-gt1_amd64.deb \
           --output libcapstone-dev_4.0.1-gt1_amd64.deb
      curl http://otsego.grammatech.com/u4/TARBALLS/debloat/pkgs/libcapstone-dev_4.0.1-gt2_amd64.deb \
           --output libcapstone-dev_4.0.1-gt2_amd64.deb
            curl http://otsego.grammatech.com/u4/TARBALLS/debloat/pkgs/libcapstone-dev_4.0.1-gt3_amd64.deb \
           --output libcapstone-dev_4.0.1-gt3_amd64.deb
      dpkg-scanpackages -m . /dev/null | gzip -9c > Packages.gz
      ;;
  esac
  popd
};

rm -rf /tmp/pdb/

setup_repo ubuntu16 xenial
setup_repo ubuntu18 bionic
setup_repo ubuntu20 focal

# No pacman packages for now
# setup_repo arch arch

setup_repo windows-debug windows-debug
setup_repo windows-release windows-release

pushd /tmp/pdb/
symstore . $(find . -name \*.pdb)
rm -rf *-win64
popd
