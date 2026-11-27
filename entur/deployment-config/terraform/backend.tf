# One workspace per environment (dev, tst, prd). The kub-ent-jp-* workspaces are obsolete.
terraform {
  backend "gcs" {
    bucket = "ent-gcs-tfa-otp2"
  }
}
