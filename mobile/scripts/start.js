/**
 * `npm start`: runs `expo start` advertising the computer's real LAN address.
 *
 * On Windows with WSL/Docker/Hyper-V, Expo may pick a virtual adapter (e.g. 172.27.112.1) that
 * phones on the Wi-Fi cannot reach. The app derives the API address from that host, so requests
 * would stall. We pin REACT_NATIVE_PACKAGER_HOSTNAME to the Wi-Fi/Ethernet IPv4 unless it is set.
 */
const os = require('os');
const { spawn } = require('child_process');

const VIRTUAL_ADAPTER = /vEthernet|WSL|Docker|VirtualBox|VMware|Hyper-V|Loopback|vboxnet|br-|docker|veth/i;

function isPrivateLan(address) {
  return /^192\.168\./.test(address) || /^10\./.test(address);
}

function isVirtualRange(address) {
  // 172.16.0.0/12 is what WSL, Docker and Hyper-V use by default
  const match = /^172\.(\d+)\./.exec(address);
  return match !== null && Number(match[1]) >= 16 && Number(match[1]) <= 31;
}

/** Best LAN IPv4 for phones on the same network, or null. */
function pickLanAddress(interfaces) {
  const candidates = [];
  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const entry of addresses ?? []) {
      const ipv4 = entry.family === 'IPv4' || entry.family === 4;
      if (!ipv4 || entry.internal || entry.address.startsWith('169.254.')) {
        continue;
      }
      candidates.push({ name, address: entry.address });
    }
  }
  const real = candidates.filter(c => !VIRTUAL_ADAPTER.test(c.name) && !isVirtualRange(c.address));
  const best = real.find(c => isPrivateLan(c.address)) ?? real[0];
  return best ? best.address : null;
}

module.exports = { pickLanAddress };

if (require.main === module) {
  const env = { ...process.env };
  if (!env.REACT_NATIVE_PACKAGER_HOSTNAME) {
    const address = pickLanAddress(os.networkInterfaces());
    if (address) {
      env.REACT_NATIVE_PACKAGER_HOSTNAME = address;
      console.log(`Using ${address} for Expo and the API (set REACT_NATIVE_PACKAGER_HOSTNAME to override).`);
    }
  }
  const child = spawn('npx', ['expo', 'start', ...process.argv.slice(2)], {
    stdio: 'inherit',
    env,
    shell: process.platform === 'win32',
  });
  child.on('exit', code => process.exit(code ?? 0));
}
