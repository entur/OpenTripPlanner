# Adopts the existing resources. Remove once applied in all environments.
import {
  to = google_pubsub_topic.otp2_outbound_bigdaddy_topic
  id = "projects/${var.gcp_resources_project}/topics/${var.otp2_outbound_bigdaddy_topic}"
}

import {
  to = google_pubsub_topic.otp2_outbound_bigdaddy_response_topic
  id = "projects/${var.gcp_resources_project}/topics/${var.otp2_outbound_bigdaddy_response_topic}"
}

import {
  to = google_pubsub_subscription.otp2-request-debug-subscription
  id = "projects/${var.gcp_resources_project}/subscriptions/${var.otp2-request-debug-subscription}"
}

import {
  to = google_storage_bucket.otp_graphs
  id = "${var.gcp_resources_project}/${var.graph_bucket_name}"
}
