# Kubernetes Demo (Arc-connected K3s)

Deploy nginx servers on the Arc-connected K3s cluster (`gpd-micropc-k3s`) — a "busy" one and an "idle" one, like the App Service demo in `../app-service`:

- **k8s/nginx-busy.yaml** — creates two deployments + services:
  - `nginx-busy` — nginx served on port 80 (generate traffic to make it busy)
  - `nginx-idle` — nginx, left idle
- **k8s/traffic-k8s.sh** — generates sustained HTTP load against `nginx-busy`
- **k8s/traffic-scheduler.sh** — runs load blocks automatically during peak hours

## Deploy

From the `azure-demo` folder:

```bash
export KUBECONFIG=~/.kube/config-gpd
kubectl apply -f k8s/nginx-busy.yaml
```

## Generate traffic

From the `azure-demo` folder:

```bash
export KUBECONFIG=~/.kube/config-gpd
./k8s/traffic-k8s.sh
```

`traffic-k8s.sh` takes two args: duration in seconds and concurrent connections, e.g. `./k8s/traffic-k8s.sh 600 10` for 10 minutes of load at 10 concurrent connections.

### Sustained load during peak hours

`traffic-scheduler.sh` runs load blocks automatically during the app's peak window (Mon–Fri **07:00–18:00 UTC**, matching the backend's peak-hour stats) and sleeps outside it. Run from the `azure-demo` folder, and leave it running in the background:

```bash
export KUBECONFIG=~/.kube/config-gpd
nohup ./k8s/traffic-scheduler.sh > /tmp/traffic-scheduler.log 2>&1 &
```

- Watch progress: `tail -f /tmp/traffic-scheduler.log`
- Stop it (kills all scheduler instances): `pkill -f traffic-scheduler.sh`
- Use your local timezone instead of UTC: `SCHEDULE_TZ=local nohup ./k8s/traffic-scheduler.sh > /tmp/traffic-scheduler.log 2>&1 &`
- Auto-start on boot via crontab: `@reboot /path/to/azure-demo/k8s/traffic-scheduler.sh >> /tmp/traffic-scheduler.log 2>&1`

Overridable env vars: `SCHEDULE_TZ` (`utc`|`local`), `SCHEDULE_FROM` (default 7), `SCHEDULE_TO` (default 18, exclusive), `RUN_DURATION` (default 600s per load block), `IDLE_SECONDS` (default 300s gap between blocks).

## Endpoints

- nginx inside cluster: `http://nginx-busy` / `http://nginx-idle` (cluster-local)