# TRMNL_OCI_APPS pairs for inker-container-image.bb's two containers,
# consumed by inker-oci-import_1.0.bb (recipes-containers/trmnl-oci-import).
# Required by that recipe -- plain variable assignments never cross from one
# recipe's own .bb file into another's, so the value has to live in shared
# included content like this, not in the image recipe alone. Instance names
# here must match the Image=/After= names inker-container-image.bb writes
# into its Quadlet units.
TRMNL_OCI_APPS = "inker-backend:inker-backend-container inker-frontend:inker-frontend-container"

# App identity for trmnl-compose-deploy (recipes-containers/trmnl-compose-deploy),
# consumed by inker-compose-deploy_1.0.bb. Kept here, not derived by
# prefix-splitting TRMNL_OCI_APPS instance names, so there stays exactly one
# place that knows this app's identity.
TRMNL_COMPOSE_APP = "inker"
