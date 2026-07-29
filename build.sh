#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
dist_dir="${project_dir}/dist"

cd "${project_dir}"
mvn clean package

mkdir -p "${dist_dir}/config-examples"
cp "${project_dir}/qdbank-server/target/qdbank-server.jar" "${dist_dir}/qdbank-server.jar"
cp "${project_dir}/qdbank-client/target/qdbank-client.jar" "${dist_dir}/qdbank-client.jar"
cp "${project_dir}/qdbank-server/src/main/resources/config.yml" "${dist_dir}/config-examples/qdbank-server-config.yml"
cp "${project_dir}/qdbank-client/src/main/resources/config.yml" "${dist_dir}/config-examples/qdbank-client-config.yml"
cp "${project_dir}/README.md" "${dist_dir}/README.md"

printf 'Build complete: %s\n' "${dist_dir}"
