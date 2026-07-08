param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RequesterId = "mock-user-001",
    [string]$ReviewerId = "admin-mock",
    [switch]$ShowPlan
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Show-DemoPlan {
    Write-Output "TicketOpsAgent backend API demo flow"
    Write-Output "1. POST /api/agent/chat"
    Write-Output "2. GET /api/tickets/{ticketId}"
    Write-Output "3. GET /api/tickets/{ticketId}/trace"
    Write-Output "4. GET /api/tickets/{ticketId}/tool-calls"
    Write-Output "5. GET /api/tickets/{ticketId}/pending-actions"
    Write-Output "6. POST /api/pending-actions/{actionId}/approve"
    Write-Output "7. GET /api/eval/reports/latest"
    Write-Output "Boundary: pending action review keeps executionStatus=NOT_EXECUTED_MOCK_ONLY."
}

function Invoke-TicketOpsJson {
    param(
        [string]$Method = "Get",
        [string]$Path,
        [object]$Body = $null
    )

    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri
    }

    $jsonBody = $Body | ConvertTo-Json -Depth 8 -Compress
    $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
    return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json; charset=utf-8" -Body $jsonBytes
}

function Write-Step {
    param(
        [string]$Text
    )

    Write-Output ""
    Write-Output "== $Text =="
}

if ($ShowPlan) {
    Show-DemoPlan
    exit 0
}

try {
    Show-DemoPlan

    $lockedText = "OA login failed, " + [char]0x9501 + [char]0x5B9A

    Write-Step "1. POST /api/agent/chat"
    $agentResponse = Invoke-TicketOpsJson -Method "Post" -Path "/api/agent/chat" -Body @{
        requesterId = $RequesterId
        title = "OA account locked demo"
        description = $lockedText
    }
    $agentResponse | ConvertTo-Json -Depth 8

    Write-Step "Find ticket id from GET /api/tickets"
    $encodedRequesterId = [uri]::EscapeDataString($RequesterId)
    $ticketList = Invoke-TicketOpsJson -Path "/api/tickets?status=OPEN&category=ACCOUNT_LOCKED&requesterId=$encodedRequesterId&page=0&size=1"
    $ticket = @($ticketList.items) | Select-Object -First 1
    if ($null -eq $ticket) {
        throw "Created ticket was not found by requesterId=$RequesterId"
    }
    $ticketId = $ticket.id
    Write-Output "ticketId=$ticketId"

    Write-Step "2. GET /api/tickets/{ticketId}"
    Invoke-TicketOpsJson -Path "/api/tickets/$ticketId" | ConvertTo-Json -Depth 8

    Write-Step "3. GET /api/tickets/{ticketId}/trace"
    Invoke-TicketOpsJson -Path "/api/tickets/$ticketId/trace" | ConvertTo-Json -Depth 8

    Write-Step "4. GET /api/tickets/{ticketId}/tool-calls"
    Invoke-TicketOpsJson -Path "/api/tickets/$ticketId/tool-calls" | ConvertTo-Json -Depth 8

    Write-Step "5. GET /api/tickets/{ticketId}/pending-actions"
    $pendingActions = Invoke-TicketOpsJson -Path "/api/tickets/$ticketId/pending-actions"
    $pendingActions | ConvertTo-Json -Depth 8
    $pendingAction = @($pendingActions) | Select-Object -First 1
    if ($null -eq $pendingAction) {
        throw "No pending action found for ticketId=$ticketId"
    }
    $actionId = $pendingAction.id

    Write-Step "6. POST /api/pending-actions/{actionId}/approve"
    $reviewResponse = Invoke-TicketOpsJson -Method "Post" -Path "/api/pending-actions/$actionId/approve" -Body @{
        reviewerId = $ReviewerId
        reviewComment = "Approved for backend API demo"
    }
    $reviewResponse | ConvertTo-Json -Depth 8
    if ($reviewResponse.executionStatus -ne "NOT_EXECUTED_MOCK_ONLY") {
        throw "Unexpected executionStatus=$($reviewResponse.executionStatus)"
    }

    Write-Step "7. GET /api/eval/reports/latest"
    Invoke-TicketOpsJson -Path "/api/eval/reports/latest" | ConvertTo-Json -Depth 8

    Write-Output ""
    Write-Output "Demo completed. No real account operation is executed."
} catch {
    Write-Error "Demo failed. Start the app with 'mvn spring-boot:run' and retry. Details: $($_.Exception.Message)"
    exit 1
}
