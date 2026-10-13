# Local development helper: starts the Bigtable emulator and prints run instructions.

Write-Host "Starting Bigtable emulator on localhost:9035..." -ForegroundColor Cyan
Start-Process -NoNewWindow gcloud -ArgumentList "beta","emulators","bigtable","start","--host-port=localhost:9035"

Write-Host ""
Write-Host "Bigtable emulator started in background." -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. In a second terminal:  `$env:SPRING_PROFILES_ACTIVE='local'; .\gradlew.bat :downstream-stub:bootRun"
Write-Host "  2. In a third terminal:   `$env:SPRING_PROFILES_ACTIVE='local'; .\gradlew.bat :loan-data-api:bootRun"
Write-Host "  3. Open Swagger UI:       http://localhost:8080/swagger-ui.html"
Write-Host ""
Write-Host "Postgres must be running on localhost:5432 with database=loandb, user=loanuser." -ForegroundColor DarkYellow
