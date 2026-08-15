# TRMNL_OCI_APPS pairs for terminus-container-image.bb's four containers,
# consumed by trmnl-oci-import_1.0.bb (recipes-containers/trmnl-oci-import).
# Required by that recipe -- plain variable assignments never cross from one
# recipe's own .bb file into another's, so the value has to live in shared
# included content like this, not in the image recipe alone. Instance names
# here must match the Image=/After= names terminus-container-image.bb writes
# into its Quadlet units.
TRMNL_OCI_APPS = "terminus-postgres:terminus-postgres-container terminus-valkey:terminus-valkey-container terminus-web:terminus-web-container terminus-worker:terminus-worker-container"

# App identity for trmnl-compose-deploy (recipes-containers/trmnl-compose-deploy),
# consumed by terminus-compose-deploy_1.0.bb. Kept here, not derived by
# prefix-splitting TRMNL_OCI_APPS instance names, so there stays exactly one
# place that knows this app's identity.
TRMNL_COMPOSE_APP = "terminus"
