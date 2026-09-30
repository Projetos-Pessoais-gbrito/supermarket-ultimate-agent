const { pickLanAddress } = require('./start');

const ipv4 = (address, internal = false) => ({ address, family: 'IPv4', internal });

describe('pickLanAddress', () => {
  it('prefers the Wi-Fi address over WSL/Docker adapters', () => {
    expect(
      pickLanAddress({
        'vEthernet (WSL)': [ipv4('172.27.112.1')],
        'Ethernet 2': [ipv4('192.168.1.129')],
        Loopback: [ipv4('127.0.0.1', true)],
      }),
    ).toBe('192.168.1.129');
  });

  it('skips the 172.16-31 range even with a neutral adapter name', () => {
    expect(pickLanAddress({ eth1: [ipv4('172.20.0.1')], wlan0: [ipv4('10.0.0.5')] })).toBe('10.0.0.5');
  });

  it('ignores IPv6, link-local and internal addresses', () => {
    expect(
      pickLanAddress({
        wlan0: [{ address: 'fe80::1', family: 'IPv6', internal: false }, ipv4('169.254.3.4'), ipv4('127.0.0.1', true)],
      }),
    ).toBeNull();
  });
});
