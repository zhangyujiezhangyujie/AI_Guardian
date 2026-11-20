param(
    [ValidateSet('Offline','Effect','PerformanceSmoke','PerformanceFormal')][string]$Action='Offline',
    [string]$Maven='mvn.cmd',
    [string]$JavaHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if($JavaHome){$env:JAVA_HOME=$JavaHome}
$common=@('-o','-Dmaven.repo.local=../.maven-cache',
    '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Users/Administrator/AppData/Local/Temp/guardian-no-unix-nonexistent')
$gatePath='../docs/experiments/performance/regression-gate.json'
function Source-Hashes {
    $hashes=[ordered]@{}
    foreach($dir in @('src/main','src/test','../knowledge-base')){
        foreach($file in Get-ChildItem -LiteralPath $dir -File -Recurse | Sort-Object FullName){
            $relative=[IO.Path]::GetRelativePath((Get-Location).Path,$file.FullName).Replace('\','/')
            $hashes[$relative]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $hashes['pom.xml']=(Get-FileHash -LiteralPath 'pom.xml' -Algorithm SHA256).Hash.ToLowerInvariant()
    return $hashes
}
switch($Action){
    'Offline' {
        # No real experiment flags. Only a successful complete run produces the receipt.
        $started=Get-Date
        & $Maven @common test
        if($LASTEXITCODE -ne 0){throw 'Full offline regression failed. No new performance gate receipt was issued.'}
        $files=@(Get-ChildItem 'target/surefire-reports/TEST-*.xml' | Where-Object {$_.LastWriteTime -ge $started.AddSeconds(-2)})
        $reports=@(); $retrievalPassed=$false
        foreach($file in $files){
            [xml]$report=Get-Content -LiteralPath $file.FullName -Raw
            if([int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.failures -ne 0){throw 'Regression XML reports a failure'}
            if($report.testsuite.name -eq 'com.aiguardian.application.RetrievalVerificationIntegrationTest'){
                $retrievalPassed=([int]$report.testsuite.tests -ge 5 -and [int]$report.testsuite.skipped -eq 0)
            }
            # Explicit experiments overwrite their own skipped XML during smoke/effect runs;
            # they are not the offline regression proof.
            if($report.testsuite.name -notmatch '(Phase4PerformanceTest|Phase4RealEvaluationTest|VerificationRealApiTest|JdbcAuditPersistenceIntegrationTest)$'){
                $reports+=@{path=[IO.Path]::GetRelativePath((Get-Location).Path,$file.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
            }
        }
        if(!$retrievalPassed){throw 'No fresh, fully passed retrieval/verification integration report'}
        New-Item -ItemType Directory -Path (Split-Path $gatePath) -Force | Out-Null
        @{allPassed=$true;retrievalIntegrationPassed=$true;completedAt=(Get-Date -Format o);sourceHashes=(Source-Hashes);reports=$reports} |
            ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $gatePath -Encoding utf8NoBOM
    }
    'Effect' {
        & $Maven @common test '-Dtest=Phase4RealEvaluationTest' '-DrunPhase4RealEvaluation=true'
        if($LASTEXITCODE -ne 0){throw 'Real module evaluation did not finish; retain any partial run records'}
    }
    {$_ -in 'PerformanceSmoke','PerformanceFormal'} {
        if(!(Test-Path -LiteralPath $gatePath)){throw 'Missing passed regression receipt. Formal performance is blocked.'}
        $kind=if($Action -eq 'PerformanceSmoke'){'smoke'}else{'formal'}
        & $Maven @common test '-Dtest=Phase4PerformanceTest' "-DrunPhase4Performance=$kind"
        if($LASTEXITCODE -ne 0){throw 'Performance environment gate or run failed; retain partial records'}
    }
}
