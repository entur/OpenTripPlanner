{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "app.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/* Generate basic labels */}}
{{- define "common.labels" }}
app: {{ template "app.name" . }}
release: {{ .Release.Name }}
team: ror
slack: talk-ror
type: api
customLogRetention: enabled
namespace: {{ .Release.Namespace }}
{{- end }}

{{/* Generate otp2 nordic labels */}}
{{- define "common.nordic.labels" }}
app: otp2nordic
release: {{ .Release.Name }}
team: ror
slack: talk-ror
type: api
namespace: {{ .Release.Namespace }}
{{- end }}

{{/* Generate graph-builder labels */}}
{{- define "graph.builder.labels" }}
app: graph-builder-otp2
release: graph-builder-otp2
team: ror
slack: talk-ror
type: api
namespace: {{ .Release.Namespace }}
{{- end }}

{{/* Generate graph-builder labels */}}
{{- define "nordic.graph.builder.labels" }}
app: graph-builder-otp2-nordic
release: graph-builder-otp2-nordic
team: ror
slack: talk-ror
type: api
namespace: {{ .Release.Namespace }}
{{- end }}
