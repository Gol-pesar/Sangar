#!/usr/bin/env python3
import asyncio, socket, struct, sys, os, hmac, hashlib
import websockets

# استفاده از موتور C برای I/O (اگر نصب باشد)
try:
    import uvloop
    asyncio.set_event_loop_policy(uvloop.EventLoopPolicy())
except ImportError:
    pass

UUID        = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
WORKER_HOST = "twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev"
WORKER_URL  = f"wss://{WORKER_HOST}/"
CLEAN_IP    = "172.67.70.244"
LISTEN      = ("127.0.0.1", 40446)
SUBPROTO    = "chat.v2"
MAX_CHUNK   = 65536

_WS_MAJOR = int(websockets.__version__.split(".")[0]) if hasattr(websockets, "__version__") else 12
_HEADERS_KWARG = "additional_headers" if _WS_MAJOR >= 14 else "extra_headers"

# کش کردن متغیرهای پرکاربرد
BO = sys.byteorder
_int_from_bytes = int.from_bytes

def xor_buf_fast(b, pre_expanded_key):
    if not b:
        return b
    l = len(b)
    val_b = _int_from_bytes(b, BO)
    val_k = _int_from_bytes(pre_expanded_key[:l], BO)
    return (val_b ^ val_k).to_bytes(l, BO)

def derive_key(nonce):
    tb = UUID.encode()
    return bytes(tb[i % len(tb)] ^ nonce[i] for i in range(16))

def build_init(host, port, payload=b""):
    nonce = os.urandom(16)
    key = derive_key(nonce)
    try:
        socket.inet_pton(socket.AF_INET, host); at, addr = 1, socket.inet_aton(host)
    except OSError:
        try:
            socket.inet_pton(socket.AF_INET6, host); at, addr = 3, socket.inet_pton(socket.AF_INET6, host)
        except OSError:
            hb = host.encode(); at, addr = 2, bytes([len(hb)]) + hb
    body = bytes([1]) + struct.pack("!H", port) + bytes([at]) + addr + payload
    
    init_key_expanded = key * (len(body) // len(key)) + key[:len(body) % len(key)]
    enc = (_int_from_bytes(body, BO) ^ _int_from_bytes(init_key_expanded, BO)).to_bytes(len(body), BO)
    
    mac = hmac.new(key, enc, hashlib.sha256).digest()[:8]
    return nonce + enc + mac, key

async def _try_connect(url, headers, server_hostname):
    kw = {
        "max_size": None,
        "open_timeout": 15,
        "subprotocols": [SUBPROTO],
        "server_hostname": server_hostname,
        "compression": None,    # فشرده‌سازی خاموش (برای سرعت)
        "ping_interval": None,
    }
    kw[_HEADERS_KWARG] = headers
    return await websockets.connect(url, **kw)

async def ws_connect():
    if CLEAN_IP:
        ip = f"[{CLEAN_IP}]" if ":" in CLEAN_IP else CLEAN_IP
        url = WORKER_URL.replace(WORKER_HOST, ip)
        try:
            return await _try_connect(url, {"Host": WORKER_HOST}, WORKER_HOST)
        except Exception as e:
            print(f"[!] clean IP {CLEAN_IP} failed: {e} — falling back to domain", file=sys.stderr)
    return await _try_connect(WORKER_URL, {}, WORKER_HOST)

async def handler(r, w):
    try:
        try:
            h = await asyncio.wait_for(r.readexactly(2), timeout=10)
        except (asyncio.TimeoutError, asyncio.IncompleteReadError):
            return
        if h[0] != 5: return
        try:
            await asyncio.wait_for(r.readexactly(h[1]), timeout=10)
        except (asyncio.TimeoutError, asyncio.IncompleteReadError):
            return
        w.write(b"\x05\x00"); await w.drain()

        try:
            q = await asyncio.wait_for(r.readexactly(4), timeout=10)
        except (asyncio.TimeoutError, asyncio.IncompleteReadError):
            return
        if q[0] != 5 or q[1] != 1: return
        at = q[3]
        if at == 1: host = socket.inet_ntoa(await r.readexactly(4))
        elif at == 3:
            l = (await r.readexactly(1))[0]; host = (await r.readexactly(l)).decode()
        elif at == 4: host = socket.inet_ntop(socket.AF_INET6, await r.readexactly(16))
        else: return
        port = struct.unpack("!H", await r.readexactly(2))[0]

        print(f"[+] {host}:{port}", file=sys.stderr)
        
        sock = w.get_extra_info('socket')
        if sock:
            try:
                # بافرهای سیستم عامل روی مقادیر امن (1MB) تنظیم شدند تا کرش نکنند
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1048576) 
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 1048576) 
            except Exception:
                pass

        try:
            ws = await ws_connect()
        except Exception as e:
            print(f"[-] WS fail: {e}", file=sys.stderr)
            return

        init, key = build_init(host, port)
        await ws.send(init)
        
        # پیش‌تولید کلید (برای سرعت بالا و عدم درگیری رم)
        pre_expanded_key = key * ((MAX_CHUNK // len(key)) + 1)
        
        try:
            ack = await asyncio.wait_for(ws.recv(), timeout=10)
        except Exception as e:
            print(f"[-] ack fail: {e}", file=sys.stderr)
            try: await ws.close()
            except: pass
            return

        if not isinstance(ack, (bytes, bytearray)) or len(ack) < 16:
            print(f"[-] bad ack len={len(ack) if ack else 0}", file=sys.stderr)
            try: await ws.close()
            except: pass
            return
            
        expect = hmac.new(key, b"OK", hashlib.sha256).digest()[:16]
        if xor_buf_fast(bytes(ack), pre_expanded_key) != expect:
            print(f"[-] ack mismatch", file=sys.stderr)
            try: await ws.close()
            except: pass
            return

        w.write(b"\x05\x00\x00\x01" + b"\x00"*4 + b"\x00\x00"); await w.drain()

        async def up():
            try:
                while True:
                    d = await r.read(MAX_CHUNK)
                    if not d: break
                    await ws.send(xor_buf_fast(d, pre_expanded_key))
            except: pass
            finally:
                try: await ws.close()
                except: pass
                
        async def down():
            try:
                async for m in ws:
                    if isinstance(m, (bytes, bytearray)) and m:
                        w.write(xor_buf_fast(bytes(m), pre_expanded_key))
                        await w.drain()
            except: pass
            finally:
                try: w.close()
                except: pass
                
        await asyncio.gather(up(), down())
    except Exception as e:
        print(f"[-] {e}", file=sys.stderr)
    finally:
        try: w.close()
        except: pass

async def main():
    print(f"SOCKS5 127.0.0.1:40446 → {WORKER_HOST}")
    print(f"         clean IP: {CLEAN_IP or '(none, using domain)'}")
    
    # بازگشت به تنظیمات پیش‌فرض امن برای start_server
    srv = await asyncio.start_server(handler, LISTEN[0], LISTEN[1])
    async with srv: await srv.serve_forever()

if __name__ == "__main__":
    try: asyncio.run(main())
    except KeyboardInterrupt: pass
