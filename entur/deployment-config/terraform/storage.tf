# OTP graphs: written by marduk, read by otp2. Created by hand in 2023.
resource "google_storage_bucket" "otp_graphs" {
  name                        = var.graph_bucket_name
  project                     = var.gcp_resources_project
  location                    = "EUROPE-WEST4"
  storage_class               = "STANDARD"
  labels                      = var.labels
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = true
  }

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  lifecycle_rule {
    condition {
      age = 180
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type          = "SetStorageClass"
      storage_class = "COLDLINE"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

# marduk overwrites and deletes graphs, so objectCreator is not enough.
resource "google_storage_bucket_iam_member" "marduk_graph_writer" {
  bucket = google_storage_bucket.otp_graphs.name
  role   = "roles/storage.objectUser"
  member = "serviceAccount:${var.marduk_service_account}"
}
