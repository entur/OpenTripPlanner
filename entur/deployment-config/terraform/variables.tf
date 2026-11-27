variable "labels" {
  description = "Labels used in all resources"
  type        = map(string)
  default = {
    manager = "terraform"
    team    = "ror"
    slack   = "talk-ror"
    app     = "otp2"
  }
}

variable "gcp_resources_project" {
  description = "The GCP project hosting the otp2 resources"
  type        = string
}

variable "otp2_outbound_bigdaddy_topic" {
  description = "Topic name for outbound request messages to bigdaddy"
  type        = string
  default     = "ror.otp2.outbound.bigdaddy"
}

variable "otp2_outbound_bigdaddy_response_topic" {
  description = "Topic name for outbound response messages to bigdaddy"
  type        = string
  default     = "ror.otp2.outbound.bigdaddy.tripresponse"
}

variable "otp2-request-debug-subscription" {
  description = "Subscription name from otp2 request debug"
  type        = string
  default     = "ror-otp2-request-debug-subscription"
}

variable "graph_bucket_name" {
  description = "The bucket holding the OTP graphs"
  type        = string
}

variable "marduk_service_account" {
  description = "Email of the marduk runtime service account, which publishes the graphs"
  type        = string
}
