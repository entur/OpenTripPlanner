#Enviroment variables
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

variable "service_account" {
  description = "service account to access resources"
}


variable "pubsub_project" {
  description = "The GCP pubsub project id"
}


variable "otp2_outbound_bigdaddy_topic" {
  description = "Topic name for outbound request messages to bigdaddy"
  default = "ror.otp2.outbound.bigdaddy"
}

variable "otp2_outbound_bigdaddy_response_topic" {
  description = "Topic name for outbound response messages to bigdaddy"
  default = "ror.otp2.outbound.bigdaddy.tripresponse"
}

variable "otp2-request-debug-subscription" {
  description = "Subscription name from otp2 request debug"
  default = "ror-otp2-request-debug-subscription"
}

