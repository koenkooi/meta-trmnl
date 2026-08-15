# TRMNL_DEPLOY_METHOD: "podman" (default) runs each app's containers via
# its Quadlet units (today's behaviour, unchanged). "docker" runs them via
# podman-compose/docker-compose instead, against the same images and named
# volumes -- prep for a real docker/docker-moby engine later; this layer
# stays on podman either way for now. Required directly by both the
# *-container-image.bb recipes (need it at IMAGE_PREPROCESS_COMMAND parse
# time) and the *-compose-deploy_1.0.bb package recipes, since a plain
# variable assignment in one recipe's .inc never crosses into another
# recipe's own parse on its own -- see trmnl-compose-deploy.inc.
TRMNL_DEPLOY_METHOD ??= "podman"
