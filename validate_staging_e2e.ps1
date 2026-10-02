$ErrorActionPreference = "Stop"

function Assert-Equal($actual, $expected, $message) {
    if ($actual -ne $expected) {
        Write-Error "ASSERTION FAILED: $message. Expected: $expected, Actual: $actual"
    } else {
        Write-Host " [PASS] $message" -ForegroundColor Green
    }
}

Write-Host "=== Staging E2E Workflow Validation ===" -ForegroundColor Cyan

# 1. AUTHENTICATION & TOKENS
Write-Host "`n1. Authentication & Token Management..." -ForegroundColor Yellow
$adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = "admin@erp.com"; password = "admin123" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $adminLogin.role "ADMIN" "Admin login successful"
$adminToken = $adminLogin.accessToken

$managerLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = "manager@erp.com"; password = "manager123" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $managerLogin.role "MANAGER" "Manager login successful"
$managerToken = $managerLogin.accessToken

$employeeLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = "employee@erp.com"; password = "employee123" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $employeeLogin.role "EMPLOYEE" "Employee login successful"
$employeeToken = $employeeLogin.accessToken

# Token Refresh
$refreshRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/refresh" -Method Post -Body (@{ refreshToken = $adminLogin.refreshToken } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal ($refreshRes.accessToken.Length -gt 20) $true "Token refresh generated new access token"

# Invalid Token Rejection
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Headers @{ Authorization = "Bearer invalid.token.value" }
    Write-Error "Expected 401 on invalid token"
} catch {
    Assert-Equal $_.Exception.Response.StatusCode 401 "Invalid token properly rejected with 401"
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# 2. USER MANAGEMENT & RBAC
Write-Host "`n2. User Management & Role-Based Access Control..." -ForegroundColor Yellow
# Employee cannot create user (403)
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Headers @{ Authorization = "Bearer $employeeToken" } -Body (@{ fullName="Bob Test"; email="bob_$runId@test.com"; password="password123"; role="EMPLOYEE" } | ConvertTo-Json) -ContentType "application/json"
    Write-Error "Employee should not be allowed to create user"
} catch {
    Assert-Equal $_.Exception.Response.StatusCode 403 "Employee forbidden from creating user (403)"
}

# Admin creates user
$newUser = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -Body (@{ fullName="Bob Test"; email="bob_$runId@test.com"; password="password123"; role="EMPLOYEE" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $newUser.email "bob_$runId@test.com" "Admin created new user bob_$runId@test.com"

# Admin updates user role
$roleUpdate = Invoke-RestMethod -Uri "http://localhost:8080/api/users/$($newUser.id)/role" -Method Patch -Headers @{ Authorization = "Bearer $adminToken" } -Body (@{ role = "MANAGER" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $roleUpdate.role "MANAGER" "User role updated to MANAGER"

# Last admin demote protection
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/users/$($adminLogin.id)/role" -Method Patch -Headers @{ Authorization = "Bearer $adminToken" } -Body (@{ role = "EMPLOYEE" } | ConvertTo-Json) -ContentType "application/json"
    Write-Error "Expected error when demoting last admin"
} catch {
    Assert-Equal $_.Exception.Response.StatusCode 400 "Last admin demote blocked with 400"
}

# 3. PRODUCT & INVENTORY MANAGEMENT
Write-Host "`n3. Product & Inventory Management..." -ForegroundColor Yellow
$sku = "STG-WIDGET-$runId"
$prodReq = @{
    sku = $sku
    name = "Staging Widget Deluxe"
    category = "Electronics"
    unitPrice = 45.00
    quantityInStock = 20
    reorderLevel = 10
    unitOfMeasure = "PCS"
} | ConvertTo-Json

$product = Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -Body $prodReq -ContentType "application/json"
Assert-Equal $product.sku $sku "Product $sku created"
Assert-Equal $product.quantityInStock 20 "Initial stock is 20"
$productId = $product.id

# Product update (adjusting stock count to 35)
$updateReq = @{
    sku = $sku
    name = "Staging Widget Deluxe"
    category = "Electronics"
    unitPrice = 45.00
    quantityInStock = 35
    reorderLevel = 10
} | ConvertTo-Json
$adjusted = Invoke-RestMethod -Uri "http://localhost:8080/api/products/$productId" -Method Put -Headers @{ Authorization = "Bearer $adminToken" } -Body $updateReq -ContentType "application/json"
Assert-Equal $adjusted.quantityInStock 35 "Stock updated to 35"

# 4. CUSTOMERS & SUPPLIERS
Write-Host "`n4. Customer & Supplier Management..." -ForegroundColor Yellow
$cust = Invoke-RestMethod -Uri "http://localhost:8080/api/customers" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -Body (@{ name="Staging Corp"; email="staging@corp.com"; phone="555-0199"; address="123 Staging Ave" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $cust.name "Staging Corp" "Customer Staging Corp created"
$custId = $cust.id

$supp = Invoke-RestMethod -Uri "http://localhost:8080/api/suppliers" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -Body (@{ name="Global Staging Parts"; email="info@stagingparts.com"; phone="555-0188"; address="456 Industrial Way" } | ConvertTo-Json) -ContentType "application/json"
Assert-Equal $supp.name "Global Staging Parts" "Supplier Global Staging Parts created"
$suppId = $supp.id

# 5. PURCHASE ORDER & RECEIVE
Write-Host "`n5. Purchase Order Workflow..." -ForegroundColor Yellow
$poReq = @{
    partyId = $suppId
    items = @(
        @{ productId = $productId; quantity = 15; unitPrice = 30.00 }
    )
} | ConvertTo-Json

$po = Invoke-RestMethod -Uri "http://localhost:8080/api/purchase-orders" -Method Post -Headers @{ Authorization = "Bearer $managerToken" } -Body $poReq -ContentType "application/json"
Assert-Equal $po.status "DRAFT" "Purchase Order created in DRAFT status"
$poId = $po.id

# Receive Purchase Order
$receivedPo = Invoke-RestMethod -Uri "http://localhost:8080/api/purchase-orders/$poId/receive" -Method Post -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal $receivedPo.status "COMPLETED" "Purchase Order marked COMPLETED"

# Verify stock incremented: 35 + 15 = 50
$prodAfterPO = Invoke-RestMethod -Uri "http://localhost:8080/api/products/$productId" -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal $prodAfterPO.quantityInStock 50 "Stock successfully incremented from 35 to 50"

# 6. SALES ORDER & COMPLETE
Write-Host "`n6. Sales Order Workflow..." -ForegroundColor Yellow
$soReq = @{
    partyId = $custId
    items = @(
        @{ productId = $productId; quantity = 20; unitPrice = 45.00 }
    )
} | ConvertTo-Json

$so = Invoke-RestMethod -Uri "http://localhost:8080/api/sales-orders" -Method Post -Headers @{ Authorization = "Bearer $managerToken" } -Body $soReq -ContentType "application/json"
Assert-Equal $so.status "DRAFT" "Sales Order created in DRAFT status"
$soId = $so.id

# Complete Sales Order
$completedSo = Invoke-RestMethod -Uri "http://localhost:8080/api/sales-orders/$soId/complete" -Method Post -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal $completedSo.status "COMPLETED" "Sales Order marked COMPLETED"

# Verify stock deducted: 50 - 20 = 30
$prodAfterSO = Invoke-RestMethod -Uri "http://localhost:8080/api/products/$productId" -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal $prodAfterSO.quantityInStock 30 "Stock successfully deducted from 50 to 30"

# 7. INSUFFICIENT STOCK HANDLING
Write-Host "`n7. Insufficient Stock Validation..." -ForegroundColor Yellow
$oversellReq = @{
    partyId = $custId
    items = @(
        @{ productId = $productId; quantity = 40; unitPrice = 45.00 } # Stock is 30, requesting 40
    )
} | ConvertTo-Json

$oversellOrder = Invoke-RestMethod -Uri "http://localhost:8080/api/sales-orders" -Method Post -Headers @{ Authorization = "Bearer $managerToken" } -Body $oversellReq -ContentType "application/json"
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/sales-orders/$($oversellOrder.id)/complete" -Method Post -Headers @{ Authorization = "Bearer $managerToken" }
    Write-Error "Order completion should have failed due to insufficient stock"
} catch {
    Assert-Equal $_.Exception.Response.StatusCode 400 "Insufficient stock rejected with 400 Bad Request"
}

# Verify stock unchanged at 30
$prodAfterOversell = Invoke-RestMethod -Uri "http://localhost:8080/api/products/$productId" -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal $prodAfterOversell.quantityInStock 30 "Stock remained untouched at 30 after rejected oversell"

# 8. NOTIFICATIONS & AUDIT LOGS
Write-Host "`n8. Notifications & Audit Logs..." -ForegroundColor Yellow
$notifs = Invoke-RestMethod -Uri "http://localhost:8080/api/notifications" -Headers @{ Authorization = "Bearer $managerToken" }
Assert-Equal ($notifs.totalAlerts -ge 0) $true "Notifications endpoint returned summary successfully"

# Audit Logs (Admin can read, Employee forbidden)
$auditLogs = Invoke-RestMethod -Uri "http://localhost:8080/api/audit-logs" -Headers @{ Authorization = "Bearer $adminToken" }
Assert-Equal ($auditLogs.totalElements -gt 0) $true "Audit logs contain recorded system operations"

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/audit-logs" -Headers @{ Authorization = "Bearer $employeeToken" }
    Write-Error "Employee should not be allowed to read audit logs"
} catch {
    Assert-Equal $_.Exception.Response.StatusCode 403 "Employee forbidden from audit logs (403)"
}

Write-Host "`n>>> ALL STAGING E2E WORKFLOW TESTS PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
