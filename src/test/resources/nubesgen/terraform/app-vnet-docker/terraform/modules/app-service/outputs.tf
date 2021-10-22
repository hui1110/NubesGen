output "application_hostname" {
  value       = "https://${azurerm_app_service.application.default_site_hostname}"
  description = "The Web application URL."
}

output "application_caf_name" {
  value       = azurecaf_name.app_service.result
  description = "The application name generated by the Azure Cloud Adoption Framework."
}

output "container_registry_name" {
  value       = azurecaf_name.container_registry.result
  description = "The Docker Container Registry name generated by the Azure Cloud Adoption Framework."
}
