$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$narrator = New-Object System.Speech.Synthesis.SpeechSynthesizer
$english = $narrator.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'en-US' } | Select-Object -First 1
if ($english) { $narrator.SelectVoice($english.VoiceInfo.Name) }
$narrator.Rate = 0
$narrator.Volume = 100
$segments = @(
    'Meet SignalPilot: evidence, decisions, and verified recovery in one incident command center. A small engineering team is facing checkout failures after a release. This reproducible scenario supplies simulated alerts, deployment events, and diagnostic observations. The backend is Java and Spring Boot, with persistent incident state and an official Model Context Protocol server.',
    'Analysis links the deployment to connection pool exhaustion, and cites the exact evidence behind that hypothesis. It also preserves an alternative explanation. The evidence strength is a transparent rule match, not a made-up probability. An upstream provider scenario recommends a different response. If evidence is insufficient, SignalPilot declines to propose remediation.',
    'A recommendation is not permission to act. The operator reviews and approves this specific proposal, then runs a simulation. No production infrastructure is changed. New evidence invalidates earlier approvals. Revision checks and database transactions prevent stale or concurrent decisions from silently overwriting one another, and repeated request identifiers make retries safe.',
    'Recovery is measured, not assumed. One healthy check is not enough. A failed check resets the gate. Only two consecutive samples below one percent errors and five hundred milliseconds latency unlock resolution. These are controlled simulator observations, making both successful and failed recovery paths easy for judges to reproduce.',
    'With recovery verified, the operator closes the incident. Customer updates reflect the current state without exposing internal diagnostic details. The postmortem carries the evidence, decisions, approval history, and follow-up work into a portable Markdown document. The timeline is stored on disk and survives an application restart.',
    'An AI agent connects over Streamable HTTP and discovers nine real MCP tools. An independent client has verified tool discovery and execution, including incident analysis and postmortem generation. The dashboard uses labeled local logic; optional Bedrock briefing requires an AWS account. SignalPilot targets the Alexa plus self-hosted MCP track: a testable backend and a complete incident workflow.'
)
for ($index=0; $index -lt $segments.Count; $index++) {
    $path = Join-Path (Get-Location) ('artifacts/narration-' + ($index+1) + '.wav')
    $narrator.SetOutputToWaveFile($path)
    $narrator.Speak($segments[$index])
    $narrator.SetOutputToNull()
}
$narrator.Dispose()
Write-Output 'Created six English narration segments using the installed Windows voice.'
