# TRMNL device-to-server pairing

How a stock TRMNL e-ink display is pointed at a self-hosted BYOS server instead of the vendor cloud, and the HTTP contract a server must satisfy once it is.
Established by reading `github.com/usetrmnl/firmware` (GPL-3.0) directly, not from documentation, which does not cover this.

## Pointing a device at a self-hosted server

Hold the device's back button for about five seconds to raise its captive portal, then use the hidden **Advanced Configuration -> Custom Server** field to type a base URL, e.g. `http://192.168.1.50:2300`.
No firmware reflash, no DNS override, no compile-time define -- this works on any stock device running firmware 1.4.6 or newer (released 2025-02-11; any device shipping today is well past this).

The URL is persisted on-device and prefixed onto every API call the device makes.
Changing it wipes the device's cached pairing (`api_key`/`friendly_id`), forcing it to re-pair against the new server on the next check-in.
Clearing the field reverts the device to the vendor cloud (`https://trmnl.app`).

TLS is never required: the firmware calls `setInsecure()` unconditionally on every HTTPS connection, and plain `http://` on an arbitrary port is a fully supported, first-class base URL.
A self-hosted server therefore needs no certificate at all -- but this also means the device has no transport authentication of the server whatsoever, a LAN-trust assumption worth calling out to anyone deploying one, not a functional blocker.

## The HTTP contract a server must implement

Every request identifies the physical device by MAC address; `GET /api/setup` additionally sends `Content-Type`, `FW-Version`, and `Model` headers, with no auth token.
`GET /api/display` adds an `Access-Token` header plus refresh-rate, battery, and RSSI telemetry.

`/api/setup` responds with JSON containing `status`, `api_key`, `friendly_id`, and `image_url`.
`/api/display` responds with `status`, `image_url`, `refresh_rate` (seconds), and related fields.
Images must be either a 1-bit BMP at exactly 800x480 pixels (48062 bytes total), or a PNG selected via `Content-Type: image/png`.

**The single most common deployment mistake**: a server's own configured base URL (its `API_URI` setting) and the URL typed into the device's captive portal must be byte-identical, because the server embeds that URL into the absolute `image_url` it hands back.
A mismatch provisions the device successfully but leaves the screen blank -- the device never complains, it just never fetches an image.
