# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 4.84.0"
    }
  }
}


resource "google_pubsub_topic" "otp2_outbound_bigdaddy_topic" {
  project = var.pubsub_project
  name = var.otp2_outbound_bigdaddy_topic
  labels = var.labels
}


resource "google_pubsub_topic" "otp2_outbound_bigdaddy_response_topic" {
  project = var.pubsub_project
  name = var.otp2_outbound_bigdaddy_response_topic
  labels = var.labels
}

resource "google_pubsub_subscription" "otp2-request-debug-subscription" {
  name  = var.otp2-request-debug-subscription
  topic = google_pubsub_topic.otp2_outbound_bigdaddy_topic.name
  project = var.pubsub_project
  labels = var.labels
  # 2 hours
  message_retention_duration = "7200s"
  retain_acked_messages = true
  # never expire
  expiration_policy {
    ttl = ""
  }

  ack_deadline_seconds = 10

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  enable_message_ordering = false

}