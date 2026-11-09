# Run once on your PC to move your Beacon data to the server:
#   powershell -File deploy/migrate-from-pc.ps1
# It dumps the local database (read only; your PC's copy is left as it is), copies the dump to
# the server, and prints the command that restores it there. Stop using Beacon on the PC
# afterwards, or later changes there won't reach the server.

$ErrorActionPreference = 'Stop'
$container = 'beacon-java-db-1'
$dump = "beacon-migrate-$(Get-Date -Format 'yyyy-MM-dd-HHmm').dump"

Write-Host "Dumping the database in $container..."
# Inside the container, so no Postgres tools are needed on Windows. -Fc is pg_dump's compressed format.
docker exec $container pg_dump -U postgres -d beacon -Fc -f /tmp/beacon-migrate.dump
docker cp "${container}:/tmp/beacon-migrate.dump" $dump
docker exec $container rm /tmp/beacon-migrate.dump
Write-Host "Wrote $dump ($([math]::Round((Get-Item $dump).Length / 1MB, 1)) MB)"

Write-Host 'Copying it to the server...'
scp $dump "server:beacon/$dump"
Remove-Item $dump

Write-Host ''
Write-Host 'Done. To load it on the server, run:'
Write-Host "  ssh -t server 'cd beacon && ./restore.sh $dump && rm $dump'"
