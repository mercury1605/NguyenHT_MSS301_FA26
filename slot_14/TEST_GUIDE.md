# Hướng dẫn kiểm thử step-by-step – Saga Demo (Order Saga – Orchestration)

> Tài liệu đi kèm: `slot_13/Saga_guide.md` (mục 6 – Chạy và kiểm thử) và project `slot_13/saga-demo`.
> **Đã kiểm chứng:** chạy thật KB1 → KB8 trên Docker (cách B) ngày 27/10/2026 sau khi reset dữ liệu – kết quả trong tài liệu khớp output thực tế; Newman chạy collection: 43 request, 80 assertion, 0 failed.
> Mục tiêu: giúp bạn **tự tay chạy từng kịch bản KB1 → KB8**, biết **nhìn vào đâu** để kiểm tra kết quả (Postman, saga-logs, database, log của từng service) và **hiểu vì sao** kết quả lại như vậy.

---

## Mục lục

1. [Bức tranh tổng quan trước khi test](#1-bức-tranh-tổng-quan-trước-khi-test)
2. [Chuẩn bị môi trường](#2-chuẩn-bị-môi-trường)
3. [Khởi động hệ thống](#3-khởi-động-hệ-thống)
4. [Reset dữ liệu và kiểm tra dữ liệu ban đầu](#4-reset-dữ-liệu-và-kiểm-tra-dữ-liệu-ban-đầu)
5. [Bốn lớp kiểm tra kết quả của một Saga](#5-bốn-lớp-kiểm-tra-kết-quả-của-một-saga)
6. [Import Postman collection](#6-import-postman-collection)
7. [Các kịch bản kiểm thử (mục 6.3)](#7-các-kịch-bản-kiểm-thử-mục-63)
   - [7.1. KB1: Happy path](#71-kb1-happy-path)
   - [7.2. KB2: Thanh toán thất bại 402, compensate Inventory](#72-kb2-thanh-toán-thất-bại-402-compensate-inventory)
   - [7.3. KB3: Hết hàng 409, lỗi ngay bước 2](#73-kb3-hết-hàng-409-lỗi-ngay-bước-2)
   - [7.4. KB4: Sản phẩm không tồn tại 404](#74-kb4-sản-phẩm-không-tồn-tại-404)
   - [7.5. KB5: Ví không tồn tại 404](#75-kb5-ví-không-tồn-tại-404)
   - [7.6. KB6: payment-service bị tắt, network failure](#76-kb6-payment-service-bị-tắt-network-failure)
   - [7.7. KB7: Idempotent](#77-kb7-idempotent)
   - [7.8. KB8 tuỳ chọn: Compensation cũng lỗi](#78-kb8-tuỳ-chọn-compensation-cũng-lỗi)
8. [Chạy tự động bằng Collection Runner / Newman](#8-chạy-tự-động-bằng-collection-runner--newman)
9. [Mẫu ghi lại kết quả test](#9-mẫu-ghi-lại-kết-quả-test)
10. [Xử lý sự cố thường gặp khi test](#10-xử-lý-sự-cố-thường-gặp-khi-test)
11. [Câu hỏi tự kiểm tra sau khi test](#11-câu-hỏi-tự-kiểm-tra-sau-khi-test)

---

## 1. Bức tranh tổng quan trước khi test

Trước khi bấm Send bất kỳ request nào, hãy nắm chắc 3 điều sau – mọi kịch bản đều xoay quanh chúng.

### 1.1. Ba service, ba database

| Service | Cổng | Database | Cổng DB trên máy host | Vai trò trong Saga |
|---|---|---|---|---|
| `order-service` | 8081 | MySQL 8.4 – `orderdb` | 3307 | **Orchestrator** + bước 1 (tạo order) và bước cuối (confirm/cancel) |
| `inventory-service` | 8082 | MongoDB 8.0 – `inventorydb` | 27017 | Bước 2: `reserve` / compensation `release` |
| `payment-service` | 8083 | SQL Server 2022 – `paymentdb` | 14330 | Bước 3: `pay` / compensation `refund` |

### 1.2. Luồng Saga trong code (`OrderSagaOrchestrator.placeOrder`)

```
Bước 1  createPendingOrder          -> saga log CREATE_ORDER, sagaStatus = EXECUTING
Bước 2  inventoryClient.reserve     -> saga log RESERVE_INVENTORY, push(release) vào stack
Bước 3  paymentClient.pay           -> saga log PROCESS_PAYMENT,   push(refund)  vào stack
Bước 4  confirmOrder                -> saga log CONFIRM_ORDER, CONFIRMED / COMPLETED

Nếu bước 2 hoặc 3 ném RestClientException:
        saga log SAGA FAILED, sagaStatus = FAILED (lưu failureReason)
        sagaStatus = COMPENSATING
        pop từng compensation trong stack (LIFO), mỗi cái thử tối đa 3 lần
          - mỗi lần thử ghi saga log COMPENSATION SUCCESS/FAILED
        tất cả OK  -> cancelOrder: CANCELLED / COMPENSATED, saga log CANCEL_ORDER
        còn lỗi    -> dừng ở PENDING / COMPENSATING (không báo COMPENSATED giả)
```

> **Mẹo đọc kết quả:** số dòng `COMPENSATION` trong saga-logs = số compensation đã được đẩy vào stack (= số bước remote đã **thành công** trước khi lỗi), nhân với số lần thử. Lỗi ở bước 2 → stack rỗng → không có dòng `COMPENSATION` nào.

### 1.3. Dữ liệu mẫu ban đầu

| Product (MongoDB) | Tên | Giá | Tồn kho | | Ví (SQL Server) | Số dư |
|---|---|---|---|---|---|---|
| `P1` | Laptop | 1000 | 10 | | `C1` | 5000 |
| `P2` | Mouse | 20 | 5 | | `C2` | 100 |

- Product được nạp bởi `DataSeeder` (chỉ nạp khi collection rỗng).
- Ví được nạp bởi `data.sql` (viết kiểu `IF NOT EXISTS ... INSERT`, restart không lỗi).

### 1.4. Thứ tự chạy và id của order

Nếu **reset dữ liệu** rồi chạy đúng thứ tự KB1 → KB8, id của order sẽ là:

| KB | KB1 | KB2 | KB3 | KB4 | KB5 | KB6 | KB7 | KB8 |
|---|---|---|---|---|---|---|---|---|
| Order id | 1 | 2 | 3 | 4 | 5 | 6 | (không tạo order) | 7 |

> Các số liệu "kho / ví sau KB" trong tài liệu này đều tính theo **dữ liệu vừa reset + chạy đúng thứ tự**. Nếu bạn không reset, hãy so sánh **độ chênh lệch** trước/sau (Postman collection cũng làm như vậy).

---

## 2. Chuẩn bị môi trường

### 2.1. Công cụ cần có

| Công cụ | Dùng để | Kiểm tra |
|---|---|---|
| Docker Desktop (cấp **≥ 4 GB RAM**, SQL Server cần nhiều RAM) | Chạy 3 DB và (cách B) 3 service | `docker version` – phải thấy cả **Client** và **Server** |
| JDK 21 | Chạy service ở cách A | `java -version` |
| Postman **Desktop** (không dùng bản web vì không gọi được `localhost`) | Gửi request, chạy test script | Mở được app |
| (Tuỳ chọn) MySQL Workbench / DBeaver, SSMS / Azure Data Studio, MongoDB Compass | Xem dữ liệu bằng GUI | – |
| (Tuỳ chọn) Node.js + Newman | Chạy collection bằng dòng lệnh | `newman -v` |

### 2.2. Kiểm tra cổng trống

Các cổng sau trên máy host phải chưa bị chiếm: `8081, 8082, 8083, 3307, 14330, 27017`.

```powershell
# PowerShell: nếu lệnh in ra dòng nào thì cổng đó đang bị chiếm
netstat -ano | findstr ":8081 :8082 :8083 :3307 :14330 :27017"
```

> Nếu đã cài sẵn MongoDB trên máy (chiếm 27017), hãy tắt service MongoDB của Windows trước, hoặc đổi cổng bên trái trong `ports` của `docker-compose.yml`.

### 2.3. Mở terminal đúng thư mục

Mọi lệnh `docker compose ...` trong tài liệu này đều chạy trong thư mục chứa `docker-compose.yml`:

```powershell
cd "slot_13/saga-demo"
```

---

## 3. Khởi động hệ thống

Chọn **một trong hai cách**. Khuyến nghị: lần đầu dùng **cách B** cho nhanh; khi muốn debug (đặt breakpoint trong orchestrator) thì dùng **cách A**.

### 3.1. Cách B – Tất cả chạy trong Docker (khuyến nghị)

**Bước 1.** Build và chạy toàn bộ:

```powershell
docker compose up -d --build
```

Lần đầu sẽ mất vài phút (tải image + Maven build trong Dockerfile).

**Bước 2.** Kiểm tra trạng thái:

```powershell
docker compose ps
```

Kết quả mong đợi: `saga-mysql`, `saga-sqlserver`, `saga-mongodb` ở trạng thái `Up (healthy)`; `inventory-service`, `payment-service`, `order-service` ở trạng thái `Up`.

**Bước 3.** Chờ 3 service khởi động xong – xem log đến khi thấy dòng `Started ...Application`:

```powershell
docker compose logs inventory-service | Select-String "Started"
docker compose logs payment-service   | Select-String "Started"
docker compose logs order-service     | Select-String "Started"
```

**Bước 4.** Mở **3 cửa sổ terminal riêng** để theo dõi log trong suốt quá trình test (rất quan trọng cho mục 5):

```powershell
docker compose logs -f order-service       # terminal 1
docker compose logs -f inventory-service   # terminal 2
docker compose logs -f payment-service     # terminal 3
```

(Ctrl + C để thoát theo dõi, container vẫn chạy.)

### 3.2. Cách A – DB chạy Docker, service chạy IntelliJ

**Bước 1.** Chỉ chạy 3 database:

```powershell
docker compose up -d mysql sqlserver mongodb
docker compose ps      # chờ cả 3 healthy
```

**Bước 2.** Run lần lượt trong IntelliJ (hoặc `.\mvnw spring-boot:run` trong từng thư mục) theo thứ tự:
`inventory-service` → `payment-service` → `order-service`.

Các property mặc định đã trỏ tới `localhost:27017 / 14330 / 3307`, không cần cấu hình thêm.

**Bước 3.** Log của từng service hiện ngay trong tab Run của IntelliJ.

> Ở cách A, mọi lệnh `docker compose stop payment-service` trong KB6/KB8 được thay bằng **bấm Stop** service tương ứng trong IntelliJ.

### 3.3. Smoke test – 3 service đã sẵn sàng chưa?

```powershell
Invoke-RestMethod http://localhost:8082/api/inventory/products
Invoke-RestMethod http://localhost:8083/api/payments/wallets
Invoke-RestMethod http://localhost:8081/api/orders
```

Hoặc trong Postman chạy thư mục **`0 - Kiểm tra service`**. Cả 3 request phải trả **HTTP 200**.

---

## 4. Reset dữ liệu và kiểm tra dữ liệu ban đầu

Dữ liệu nằm trong Docker volume nên **không mất khi restart**. Muốn số liệu khớp đúng mục 7, hãy reset trước khi bắt đầu chuỗi KB1 → KB8.

> **Cảnh báo:** `docker compose down -v` **xoá vĩnh viễn** volume của cả 3 database (toàn bộ order, saga log, ví, kho). Chỉ dùng khi bạn chắc chắn muốn bắt đầu lại từ đầu.

```powershell
docker compose down -v
docker compose up -d --build      # cách A: docker compose up -d mysql sqlserver mongodb
```

Sau khi reset, kiểm tra dữ liệu ban đầu:

```powershell
Invoke-RestMethod http://localhost:8082/api/inventory/products | Format-Table
Invoke-RestMethod http://localhost:8083/api/payments/wallets   | Format-Table
Invoke-RestMethod http://localhost:8081/api/orders
```

Mong đợi:

| id | name | price | stock |
|---|---|---|---|
| P1 | Laptop | 1000 | 10 |
| P2 | Mouse | 20 | 5 |

| customerId | balance |
|---|---|
| C1 | 5000 |
| C2 | 100 |

Và danh sách order rỗng `[]`.

---

## 5. Bốn lớp kiểm tra kết quả của một Saga

Một kịch bản chỉ được coi là **PASS** khi cả 4 lớp dưới đây đều đúng. Mỗi KB ở mục 7 đều có checklist theo 4 lớp này.

### Lớp 1 – Response của `POST /api/orders`

```json
{ "id": 1, "customerId": "C1", "productId": "P1", "quantity": 2, "amount": 2000.0,
  "status": "CONFIRMED", "sagaStatus": "COMPLETED", "failureReason": null }
```

Xem 3 field: `status` (`PENDING | CONFIRMED | CANCELLED`), `sagaStatus` (6 trạng thái Slide 27), `failureReason`.

> Lưu ý: kể cả khi Saga thất bại, response vẫn là **HTTP 200** – vì orchestrator đã xử lý lỗi và trả về order ở trạng thái cuối. Lỗi nằm trong `status` / `sagaStatus` / `failureReason`, không nằm ở HTTP status.

### Lớp 2 – Saga log: `GET /api/orders/{id}/saga-logs`

```json
[ { "id": 1, "orderId": 1, "step": "CREATE_ORDER", "result": "SUCCESS",
    "message": "Order PENDING", "time": "2026-10-16T11:30:12.345" }, ... ]
```

Xem **thứ tự** cột `step` và cột `result`. Đây là "hộp đen" ghi lại Saga đã đi qua những bước nào.

### Lớp 3 – Dữ liệu thật trong 3 database

Qua API:

```powershell
Invoke-RestMethod http://localhost:8082/api/inventory/products       # tồn kho
Invoke-RestMethod http://localhost:8082/api/inventory/reservations   # reservation (RESERVED / RELEASED)
Invoke-RestMethod http://localhost:8083/api/payments/wallets         # số dư ví
Invoke-RestMethod http://localhost:8083/api/payments                 # payment (PAID / REFUNDED)
```

Hoặc vào thẳng database:

```powershell
# MySQL – order
docker exec -it saga-mysql mysql -uroot -proot orderdb -e "SELECT id, status, saga_status, failure_reason FROM orders; SELECT order_id, step, result, message FROM saga_log;"

# SQL Server – payment
docker exec -it saga-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Saga@12345" -C -d paymentdb -Q "SELECT * FROM wallet; SELECT * FROM payment;"

# MongoDB – inventory
docker exec -it saga-mongodb mongosh inventorydb --quiet --eval "db.product.find(); db.reservation.find()"
```

### Lớp 4 – Log của từng service

| Service | Dòng log quan trọng (lấy từ code) |
|---|---|
| inventory | `RESERVE order={id} product={p} qty={n} -> stock còn {x}` |
| inventory | `RELEASE order={id} -> trả lại {n} {p}, stock = {x}` |
| inventory | `RELEASE order={id} -> không có gì để hoàn tác` (idempotent) |
| inventory | `Order {id} đã được reserve trước đó -> bỏ qua` (idempotent) |
| payment | `PAY order={id} customer={c} amount={a} -> balance còn {b}` |
| payment | `REFUND order={id} -> hoàn {a} cho {c}, balance = {b}` |
| payment | `REFUND order={id} -> không có gì để hoàn tác` (idempotent) |
| order | `WARN ... Saga order={id} thất bại: {reason}` |
| order | `WARN ... [DEMO] Chờ {ms} ms trước khi thanh toán order={id}` (KB8) |
| order | `ERROR ... Compensation cho order={id} chưa xong, cần xử lý thủ công` (KB8) |

Lọc nhanh log theo order id (ví dụ order 2):

```powershell
docker compose logs inventory-service | Select-String "order=2"
docker compose logs payment-service   | Select-String "order=2"
docker compose logs order-service     | Select-String "order=2"
```

> Happy path **không** sinh log ở order-service (orchestrator chỉ `log.warn/error` khi có lỗi). Muốn thấy order-service "làm việc", hãy xem saga-logs (lớp 2).

---

## 6. Import Postman collection

**Bước 1.** Mở Postman Desktop → **Import** → chọn file `slot_13/saga-demo/Saga_Postman_Collection.json`.

**Bước 2.** Mở collection **"MSS301 - Saga Demo (Spring Boot 4.1.1)"** → tab **Variables**, kiểm tra:

| Biến | Giá trị |
|---|---|
| `orderUrl` | `http://localhost:8081` |
| `inventoryUrl` | `http://localhost:8082` |
| `paymentUrl` | `http://localhost:8083` |

Các biến `kb1OrderId`, `kb2OrderId`…, `stock_P1`, `bal_C1`… sẽ được test script **tự sinh** khi chạy.

**Bước 3.** Cấu trúc collection – mỗi KB theo mẫu **Snapshot → Đặt hàng → Kiểm tra kho & ví → Saga logs**:

```
MSS301 - Saga Demo (Spring Boot 4.1.1)
├── 0 - Kiểm tra service
├── KB1 - Happy path
├── KB2 - Thanh toán thất bại (402)        (+ [KB2] 5. Reservation = RELEASED)
├── KB3 - Hết hàng (409)
├── KB4 - Sản phẩm không tồn tại (404 inventory)
├── KB5 - Ví không tồn tại (404 payment)
├── KB7 - Idempotent (gọi lặp lại)
└── Thủ công (không chạy bằng Runner)
    ├── KB6 - payment-service bị tắt
    └── KB8 - Compensation cũng lỗi
```

**Cách xem kết quả test script:** sau khi Send một request, ở khung response chọn tab **Test Results** – mỗi dòng `PASS` (xanh) / `FAIL` (đỏ) là một `pm.test(...)`.

> **Quan trọng:** luôn chạy request `1. Snapshot kho & ví` của KB **trước** khi chạy `2. Đặt hàng`, vì request `3. Kiểm tra` so sánh với giá trị đã snapshot.

---

## 7. Các kịch bản kiểm thử (mục 6.3)

Bảng tóm tắt (dữ liệu vừa reset, chạy đúng thứ tự):

| KB | Request `POST /api/orders` | Lỗi ở bước | `status` / `sagaStatus` | `failureReason` | Kho / ví sau KB |
|---|---|---|---|---|---|
| KB1 | `C1, P1, 2, 2000` | – | `CONFIRMED / COMPLETED` | `null` | P1 = 8, C1 = 3000 |
| KB2 | `C2, P1, 1, 1000` | Payment (402) | `CANCELLED / COMPENSATED` | `402 - Số dư không đủ…` | P1 = 8, C2 = 100 |
| KB3 | `C1, P2, 100, 2000` | Inventory (409) | `CANCELLED / COMPENSATED` | `409 - Không đủ hàng…` | P2 = 5, C1 = 3000 |
| KB4 | `C1, P9, 1, 10` | Inventory (404) | `CANCELLED / COMPENSATED` | `404 - Không tìm thấy sản phẩm P9` | không đổi |
| KB5 | `C9, P2, 1, 20` | Payment (404) | `CANCELLED / COMPENSATED` | `404 - Không tìm thấy ví của C9` | P2 = 5 |
| KB6 | `C1, P2, 1, 20` (payment tắt) | Network | `CANCELLED / COMPENSATED` | `Service không phản hồi: I/O error…` | P2 = 5 |
| KB7 | Gọi lặp reserve/pay/release/refund | – | – | – | không đổi |
| KB8 | `C1, P2, 1, 20` (tắt payment + inventory) | Network + compensation lỗi | `PENDING / COMPENSATING` | `Service không phản hồi…` | P2 = 4 (treo) |

Với mỗi KB, có thể gửi request bằng **Postman** (khuyến nghị) hoặc **PowerShell**. Mẫu PowerShell chung:

```powershell
$body = @{ customerId = "C1"; productId = "P1"; quantity = 2; amount = 2000 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/orders -ContentType "application/json; charset=utf-8" -Body $body
```

---

### 7.1. KB1: Happy path

**Mục đích:** Saga chạy hết 4 bước, không có lỗi. Kiểm tra chuỗi local transaction trên 3 database khác loại đều được commit.

**Điều kiện trước:** dữ liệu vừa reset (P1 = 10, C1 = 5000), 3 service đang chạy.

**Các bước thực hiện:**

1. Postman → `KB1 - Happy path` → `[KB1] 1. Snapshot kho & ví` → **Send**. Tab Test Results: `HTTP 200`, `Đọc được số dư ví` đều PASS.
2. `[KB1] 2. Đặt hàng` → kiểm tra Body là:
   ```json
   { "customerId": "C1", "productId": "P1", "quantity": 2, "amount": 2000 }
   ```
   → **Send**.
3. `[KB1] 3. Kiểm tra kho & ví` → **Send**.
4. `[KB1] 4. Saga logs` → **Send**.
5. Xem log ở terminal inventory và payment.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 1`, `status = CONFIRMED`, `sagaStatus = COMPLETED`, `failureReason = null` |
| 2. Saga logs | `CREATE_ORDER (Order PENDING)` → `RESERVE_INVENTORY (Đã giữ 2 P1)` → `PROCESS_PAYMENT (Đã thu 2000.0)` → `CONFIRM_ORDER (Saga COMPLETED)`, tất cả `SUCCESS` |
| 3. Database | P1: 10 → **8**; C1: 5000 → **3000**; reservation `orderId=1` = `RESERVED`; payment `orderId=1` = `PAID` |
| 4. Log | inventory: `RESERVE order=1 product=P1 qty=2 -> stock còn 8`<br>payment: `PAY order=1 customer=C1 amount=2000.0 -> balance còn 3000.0`<br>order: không có WARN/ERROR |

**Tại sao như vậy?** Mỗi bước thành công thì push compensation vào stack, nhưng vì không có lỗi nên stack bị bỏ đi, không compensation nào chạy. Biến `kb1OrderId` được lưu để KB7 dùng lại.

**Checklist:** ☐ Response đúng ☐ 4 dòng saga log đúng thứ tự ☐ P1 = 8, C1 = 3000 ☐ Log inventory + payment có dòng RESERVE / PAY

---

### 7.2. KB2: Thanh toán thất bại 402, compensate Inventory

**Mục đích:** kịch bản *Partial Failure* (Slide 5) – kho đã trừ nhưng thanh toán lỗi. Kiểm tra Saga **hoàn tác bước 2** (`release`) rồi **hoàn tác bước 1** (`cancelOrder`).

**Điều kiện trước:** đã chạy KB1 (P1 = 8, C2 = 100).

**Các bước thực hiện:**

1. `[KB2] 1. Snapshot kho & ví` → **Send**.
2. `[KB2] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C2", "productId": "P1", "quantity": 1, "amount": 1000 }
   ```
   → **Send**. C2 chỉ có 100 nên chắc chắn lỗi 402.
3. **Ngay sau khi Send**, nhìn terminal **inventory**: sẽ thấy 2 dòng nối tiếp – trừ kho rồi trả lại kho.
4. `[KB2] 3. Kiểm tra kho & ví` → **Send**.
5. `[KB2] 4. Saga logs` → **Send**.
6. `[KB2] 5. Reservation = RELEASED` → **Send**.
7. (Tuỳ chọn) Kiểm tra trực tiếp trong MongoDB:
   ```powershell
   docker exec -it saga-mongodb mongosh inventorydb --quiet --eval "db.reservation.find({_id: 2})"
   ```

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 2`, `status = CANCELLED`, `sagaStatus = COMPENSATED`, `failureReason = "402 - Số dư không đủ: còn 100.0, cần 1000.0"` |
| 2. Saga logs | xem bảng bên dưới |
| 3. Database | P1: 8 → 7 → **8** (trừ rồi trả); C2 vẫn **100**; reservation `orderId=2` = `RELEASED`; **không có** payment cho order 2 |
| 4. Log | inventory: `RESERVE order=2 product=P1 qty=1 -> stock còn 7` rồi `RELEASE order=2 -> trả lại 1 P1, stock = 8`<br>payment: **không có** dòng `PAY order=2` (lỗi ném ra trước khi log)<br>order: `WARN ... Saga order=2 thất bại: 402 - Số dư không đủ: còn 100.0, cần 1000.0` |

Saga logs mong đợi:

| step | result | message |
|---|---|---|
| CREATE_ORDER | SUCCESS | Order PENDING |
| RESERVE_INVENTORY | SUCCESS | Đã giữ 1 P1 |
| SAGA | FAILED | 402 - Số dư không đủ: còn 100.0, cần 1000.0 |
| COMPENSATION | SUCCESS | Lần thử 1 |
| CANCEL_ORDER | SUCCESS | Saga COMPENSATED |

**Tại sao như vậy?**
- `pay` ném `ResponseStatusException(PAYMENT_REQUIRED)` → payment-service trả 402 → `RestClient.retrieve()` ném `RestClientResponseException` → orchestrator vào `catch`.
- `extractReason` đọc field `message` trong body lỗi → `"402 - Số dư không đủ…"` (cần `spring.web.error.include-message=always` ở payment-service; nếu thiếu thì chỉ thấy `402 PAYMENT_REQUIRED`).
- Stack lúc đó chỉ có 1 phần tử là `release(2)` (vì `pay` chưa thành công nên `refund` chưa được push) → chạy `release` thành công ngay lần thử 1 → `cancelOrder`.
- Toàn bộ transaction `pay` bị rollback trong SQL Server, nên ví C2 không đổi.

**Checklist:** ☐ Response `CANCELLED / COMPENSATED`, reason bắt đầu bằng `402` ☐ 5 dòng saga log đúng thứ tự ☐ P1 về lại 8, C2 = 100 ☐ Reservation 2 = `RELEASED` ☐ Log inventory có cả RESERVE và RELEASE order=2

---

### 7.3. KB3: Hết hàng 409, lỗi ngay bước 2

**Mục đích:** lỗi xảy ra ở bước remote **đầu tiên** → không có gì để hoàn tác ở service khác, chỉ cần hủy order.

**Điều kiện trước:** P2 = 5, C1 = 3000.

**Các bước thực hiện:**

1. `[KB3] 1. Snapshot kho & ví` → **Send**.
2. `[KB3] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C1", "productId": "P2", "quantity": 100, "amount": 2000 }
   ```
   → **Send**.
3. `[KB3] 3. Kiểm tra kho & ví` → **Send**.
4. `[KB3] 4. Saga logs` → **Send**.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 3`, `CANCELLED / COMPENSATED`, `failureReason = "409 - Không đủ hàng: còn 5, cần 100"` |
| 2. Saga logs | `CREATE_ORDER (SUCCESS)` → `SAGA (FAILED, 409 - Không đủ hàng: còn 5, cần 100)` → `CANCEL_ORDER (SUCCESS)` – **không có** `RESERVE_INVENTORY` và **không có** `COMPENSATION` |
| 3. Database | P2 vẫn **5**, C1 vẫn **3000**; **không có** reservation cho order 3 |
| 4. Log | inventory: **không có** dòng `RESERVE order=3` (ném 409 trước khi log)<br>payment: không có gì (không được gọi)<br>order: `WARN ... Saga order=3 thất bại: 409 - Không đủ hàng: còn 5, cần 100` |

**Tại sao như vậy?** `reserve` ném lỗi **trước** dòng `compensations.push(...)`, nên stack rỗng. Vòng `while` trong `compensate()` không chạy lần nào → gọi thẳng `cancelOrder`. Đây là câu trả lời cho câu hỏi ôn tập số 3: *"Trong KB3, vì sao không cần gọi `release`?"* – vì kho chưa từng bị trừ (transaction `reserve` trong MongoDB đã rollback/không ghi gì).

**Checklist:** ☐ Reason bắt đầu bằng `409` ☐ Saga log chỉ có 3 dòng `CREATE_ORDER, SAGA, CANCEL_ORDER` ☐ P2 = 5, C1 = 3000

---

### 7.4. KB4: Sản phẩm không tồn tại 404

**Mục đích:** giống KB3 nhưng lỗi là 404 từ inventory. Kiểm tra orchestrator xử lý mọi loại lỗi 4xx như nhau.

**Điều kiện trước:** không có product `P9`.

**Các bước thực hiện:**

1. `[KB4] 1. Snapshot kho & ví` → **Send**.
2. `[KB4] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C1", "productId": "P9", "quantity": 1, "amount": 10 }
   ```
   → **Send**.
3. `[KB4] 3. Kiểm tra kho & ví` → **Send**.
4. `[KB4] 4. Saga logs` → **Send**.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 4`, `CANCELLED / COMPENSATED`, `failureReason = "404 - Không tìm thấy sản phẩm P9"` |
| 2. Saga logs | `CREATE_ORDER` → `SAGA (FAILED)` → `CANCEL_ORDER` |
| 3. Database | P1 = 8, P2 = 5, C1 = 3000 – **không đổi gì** |
| 4. Log | order: `WARN ... Saga order=4 thất bại: 404 - Không tìm thấy sản phẩm P9`; inventory và payment không có log nghiệp vụ |

**Tại sao như vậy?** `InventoryService.reserve` ném `ResponseStatusException(NOT_FOUND)`. Với `RestClient`, cả 404, 409, 402 đều là `RestClientResponseException` (con của `RestClientException`) nên đi chung một nhánh `catch`.

**Checklist:** ☐ Reason bắt đầu bằng `404` và chứa `P9` ☐ Saga log 3 dòng ☐ Kho, ví không đổi

---

### 7.5. KB5: Ví không tồn tại 404

**Mục đích:** lỗi ở bước 3 nhưng là 404 (khác KB2 là 402). Kiểm tra `release` được gọi cho P2.

**Điều kiện trước:** P2 = 5, không có ví `C9`.

**Các bước thực hiện:**

1. `[KB5] 1. Snapshot kho & ví` → **Send**.
2. `[KB5] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C9", "productId": "P2", "quantity": 1, "amount": 20 }
   ```
   → **Send**.
3. `[KB5] 3. Kiểm tra kho & ví` → **Send**.
4. `[KB5] 4. Saga logs` → **Send**.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 5`, `CANCELLED / COMPENSATED`, `failureReason = "404 - Không tìm thấy ví của C9"` |
| 2. Saga logs | `CREATE_ORDER` → `RESERVE_INVENTORY (Đã giữ 1 P2)` → `SAGA (FAILED)` → `COMPENSATION (SUCCESS, Lần thử 1)` → `CANCEL_ORDER` – giống KB2 |
| 3. Database | P2: 5 → 4 → **5**; reservation `orderId=5` = `RELEASED` |
| 4. Log | inventory: `RESERVE order=5 product=P2 qty=1 -> stock còn 4` rồi `RELEASE order=5 -> trả lại 1 P2, stock = 5`<br>order: `WARN ... Saga order=5 thất bại: 404 - Không tìm thấy ví của C9` |

**Tại sao như vậy?** Cùng cấu trúc KB2: reserve thành công → push `release` → pay lỗi → pop `release` → cancel. Điều này chứng minh compensation không phụ thuộc vào **loại** lỗi, chỉ phụ thuộc vào **bước nào** đã thành công.

**Checklist:** ☐ Reason bắt đầu bằng `404`, chứa `C9` ☐ 5 dòng saga log như KB2 ☐ P2 về lại 5 ☐ Reservation 5 = `RELEASED`

---

### 7.6. KB6: payment-service bị tắt, network failure

**Mục đích:** lỗi không phải nghiệp vụ mà là **lỗi mạng** (service chết). Kiểm tra orchestrator vẫn compensate được.

**Điều kiện trước:** P2 = 5, C1 = 3000. Kịch bản này **chạy thủ công**, không chạy bằng Runner.

**Các bước thực hiện:**

1. Postman → `Thủ công (không chạy bằng Runner)` → `KB6 - payment-service bị tắt` → `[KB6] 1. Snapshot kho & ví` → **Send** (phải chạy **trước** khi tắt payment vì snapshot cần đọc ví).
2. Tắt payment-service:
   ```powershell
   docker compose stop payment-service
   docker compose ps          # payment-service không còn trong danh sách đang chạy
   ```
   (Cách A: bấm **Stop** payment-service trong IntelliJ.)
3. `[KB6] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C1", "productId": "P2", "quantity": 1, "amount": 20 }
   ```
   → **Send**.
4. Bật lại payment-service và chờ nó khởi động xong:
   ```powershell
   docker compose start payment-service
   docker compose logs payment-service | Select-String "Started"
   ```
5. `[KB6] 3. Kiểm tra kho & ví` → **Send** (cần payment chạy để đọc ví).
6. `[KB6] 4. Saga logs` → **Send**.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 6`, `CANCELLED / COMPENSATED`, `failureReason` bắt đầu bằng `"Service không phản hồi: I/O error on POST request for \"http://...:8083/api/payments/pay\"..."` |
| 2. Saga logs | giống KB2: `CREATE_ORDER` → `RESERVE_INVENTORY` → `SAGA (FAILED)` → `COMPENSATION (SUCCESS)` → `CANCEL_ORDER` |
| 3. Database | P2: 5 → 4 → **5**; C1 vẫn **3000** (dữ liệu nằm trong volume SQL Server, không mất khi stop/start) |
| 4. Log | inventory: `RESERVE order=6 ...` rồi `RELEASE order=6 -> trả lại 1 P2, stock = 5`<br>order: `WARN ... Saga order=6 thất bại: Service không phản hồi: I/O error ...` |

> Kết quả chạy thật (cách B, 27/10/2026): `failureReason = "Service không phản hồi: I/O error on POST request for \"http://payment-service:8083/api/payments/pay\": HTTP connect timed out"`, response về sau khoảng **2 giây** (đúng bằng `spring.http.clients.connect-timeout=2s`). Ở cách A, phần cuối thường là `Connection refused` và response về gần như ngay lập tức. Cả hai đều là `ResourceAccessException` – không có HTTP status, nên `extractReason` đi vào nhánh `"Service không phản hồi: "`.

**Tại sao như vậy?** `ResourceAccessException` cũng là con của `RestClientException`, nên cùng một khối `catch` xử lý cả lỗi 4xx và lỗi mạng. `connect-timeout=2s` đảm bảo request không treo lâu – đó là lý do Postman chờ khoảng 2 giây mới có response.

**Checklist:** ☐ Reason bắt đầu bằng `Service không phản hồi` ☐ Saga log giống KB2 ☐ P2 = 5 ☐ Đã **bật lại** payment-service

---

### 7.7. KB7: Idempotent

**Mục đích:** kiểm tra yêu cầu **Idempotent** của compensation (Slide 25): gọi lặp lại nhiều lần **không** làm sai dữ liệu. Điều này cho phép retry an toàn.

**Điều kiện trước:** đã chạy KB1 (order 1: reserved + paid) và KB2 (order 2: released, chưa từng pay). Biến `kb1OrderId`, `kb2OrderId` đã có giá trị.

**Các bước thực hiện (Postman → `KB7 - Idempotent (gọi lặp lại)`):**

| # | Request | Kết quả mong đợi | Log mong đợi |
|---|---|---|---|
| 1 | `[KB7] 1. Snapshot kho & ví` | HTTP 200 | – |
| 2 | `[KB7] 2. Release order KB2 lần 1` – `POST /api/inventory/release/2` | body `RELEASED` | inventory: `RELEASE order=2 -> không có gì để hoàn tác` |
| 3 | `[KB7] 3. Release order KB2 lần 2` – gọi lại y hệt | body `RELEASED`, P1 **vẫn 8** | như trên |
| 4 | `[KB7] 4. Reserve lại order KB1` – `POST /api/inventory/reserve` body `{"orderId":1,"productId":"P1","quantity":2}` | trả về reservation cũ `status = RESERVED`, kho **không bị trừ lần 2** | inventory: `Order 1 đã được reserve trước đó -> bỏ qua` |
| 5 | `[KB7] 5. Pay lại order KB1` – `POST /api/payments/pay` body `{"orderId":1,"customerId":"C1","amount":2000}` | trả về payment cũ `status = PAID`, ví **không bị trừ lần 2** | payment: không có dòng `PAY order=1` mới |
| 6 | `[KB7] 6. Refund order KB2 (chưa từng thanh toán)` – `POST /api/payments/refund/2` | body `REFUNDED`, không lỗi, **không cộng tiền** | payment: `REFUND order=2 -> không có gì để hoàn tác` |
| 7 | `[KB7] 7. Kiểm tra kho & ví` | mọi chênh lệch = **0** | – |

**Kết quả mong đợi tổng:** P1 = 8, P2 = 5, C1 = 3000, C2 = 100 – **không đổi** so với snapshot.

**Tại sao như vậy?**
- `reserve` và `pay` dùng `orderId` làm khoá chính của `Reservation` / `Payment` → gọi lại thì tìm thấy bản ghi cũ và trả về luôn.
- `release` / `refund` kiểm tra: chưa có bản ghi, hoặc đã `RELEASED` / `REFUNDED` → không làm gì.

> Thử thêm (ngoài collection): gọi `POST /api/inventory/release/1` để thấy `release` **có** cộng kho khi reservation đang `RESERVED` (P1 lên 10), sau đó gọi lần 2 thì không cộng nữa. Lưu ý thao tác này làm lệch số liệu của các KB sau – nên reset lại nếu muốn số "đẹp".

**Checklist:** ☐ Release 2 lần đều `RELEASED`, P1 không đổi ☐ Reserve lại không trừ kho ☐ Pay lại không trừ tiền ☐ Refund order chưa trả không cộng tiền ☐ Log có các dòng `không có gì để hoàn tác` / `bỏ qua`

---

### 7.8. KB8 tuỳ chọn: Compensation cũng lỗi

**Mục đích:** kiểm tra yêu cầu **Reliable** – compensation có retry (tối đa 3 lần), và khi vẫn thất bại thì Saga **không được** báo `COMPENSATED` giả mà phải dừng ở `COMPENSATING`.

**Ý tưởng:** cho order-service "ngủ" 15 giây giữa bước reserve và bước pay. Trong 15 giây đó tắt cả payment lẫn inventory → `pay` lỗi mạng → `release` cũng lỗi mạng 3 lần.

**Điều kiện trước:** P2 = 5, 3 service đang chạy bình thường.

**Các bước thực hiện:**

1. Khởi động lại order-service với độ trễ 15 giây:
   - PowerShell:
     ```powershell
     $env:PAYMENT_DELAY_MS=15000; docker compose up -d order-service
     ```
   - Git Bash:
     ```bash
     PAYMENT_DELAY_MS=15000 docker compose up -d order-service
     ```
   - Cách A (IntelliJ): Run Configuration của order-service → Environment variables → `PAYMENT_DELAY_MS=15000` → Rerun (hoặc đặt breakpoint ngay trước `paymentClient.pay(...)`).

   Chờ order-service log `Started OrderServiceApplication`.
2. `[KB8] 1. Snapshot kho & ví` → **Send**.
3. Chuẩn bị sẵn lệnh tắt service ở một terminal (chưa Enter):
   ```powershell
   docker compose stop payment-service inventory-service
   ```
4. `[KB8] 2. Đặt hàng` với body:
   ```json
   { "customerId": "C1", "productId": "P2", "quantity": 1, "amount": 20 }
   ```
   → **Send**. Request sẽ "treo" khoảng 15 giây. Ở terminal order-service thấy:
   `WARN ... [DEMO] Chờ 15000 ms trước khi thanh toán order=7`
5. **Trong 15 giây đó**, Enter lệnh ở bước 3.
6. Chờ Postman nhận response. Tổng thời gian khoảng **25–30 giây** (15 giây chờ + `pay` bị timeout + 3 lần `release`, mỗi lần timeout 2 giây và cách nhau 1 giây).
7. **Trước khi khởi động lại order-service**, xem và lưu log của nó – lệnh `docker compose up -d order-service` ở bước 8 sẽ **tạo lại container**, làm mất các dòng WARN/ERROR của KB8 (terminal `logs -f order-service` cũng tự thoát):
   ```powershell
   docker compose logs order-service | Select-String "order=7"
   ```
8. Bật lại các service và tắt độ trễ:
   ```powershell
   docker compose start inventory-service payment-service
   $env:PAYMENT_DELAY_MS=0; docker compose up -d order-service
   ```
   (Cách A: Start lại inventory, payment; xoá biến môi trường và Rerun order-service.)
9. `[KB8] 3. Kiểm tra kho & ví` và `[KB8] 4. Saga logs` → **Send**.

**Kết quả mong đợi:**

| Lớp | Mong đợi |
|---|---|
| 1. Response | `id = 7`, `status = PENDING`, `sagaStatus = COMPENSATING`, `failureReason` bắt đầu bằng `"Service không phản hồi: ..."` |
| 2. Saga logs | xem bảng bên dưới – **3 dòng `COMPENSATION FAILED`**, **không có** `CANCEL_ORDER` |
| 3. Database | P2 = **4** (bị giữ 1, chưa được trả); reservation `orderId=7` vẫn `RESERVED`; C1 không đổi |
| 4. Log | order: `WARN ... [DEMO] Chờ 15000 ms ...`, `WARN ... Saga order=7 thất bại: Service không phản hồi: ...`, `ERROR ... Compensation cho order=7 chưa xong, cần xử lý thủ công`<br>inventory: `RESERVE order=7 product=P2 qty=1 -> stock còn 4`, **không có** RELEASE |

Saga logs mong đợi:

| step | result | message |
|---|---|---|
| CREATE_ORDER | SUCCESS | Order PENDING |
| RESERVE_INVENTORY | SUCCESS | Đã giữ 1 P2 |
| SAGA | FAILED | Service không phản hồi: I/O error on POST request… |
| COMPENSATION | FAILED | Lần thử 1: I/O error on POST request for "http://inventory-service:8082/api/inventory/release/7": HTTP connect timed out |
| COMPENSATION | FAILED | Lần thử 2: (như trên) |
| COMPENSATION | FAILED | Lần thử 3: (như trên) |

> Khi chạy thật, phần cuối message của dòng `SAGA` có thể là `": null"` hoặc `HTTP connect timed out` tuỳ thời điểm container bị dừng – chỉ cần bắt đầu bằng `Service không phản hồi: I/O error`.

**Bước khôi phục thủ công (quan sát thêm):**

```powershell
Invoke-RestMethod http://localhost:8082/api/inventory/products        # P2 = 4
Invoke-RestMethod -Method Post http://localhost:8082/api/inventory/release/7   # an toàn vì idempotent
Invoke-RestMethod http://localhost:8082/api/inventory/products        # P2 = 5
```

> Gọi tay `release` chỉ trả lại kho; order 7 vẫn `PENDING / COMPENSATING` vì demo chưa có API khôi phục order. Đó chính là bài tập mở rộng **EXT-4** (job `@Scheduled` hoặc API `POST /api/orders/{id}/compensate`).

**Tại sao như vậy?**
- Lúc lỗi, stack chỉ có `release(7)` (pay chưa thành công). `RetryTemplate` với `maxRetries(2)` = 1 lần đầu + 2 lần thử lại = **3 lần**, mỗi lần đều ghi `COMPENSATION FAILED`.
- Hết lượt thử → `RetryException` → `allOk = false` → `compensate()` `return` sớm, **không** gọi `cancelOrder`. Vì `cancelOrder` là nơi duy nhất đổi `status`, order giữ `PENDING`.
- Đây là "trạng thái treo" cần cơ chế phục hồi – minh hoạ vì sao compensation phải **Reliable**.

**Checklist:** ☐ Response `PENDING / COMPENSATING` ☐ Đúng 3 dòng `COMPENSATION FAILED`, không có `CANCEL_ORDER` ☐ P2 = 4 trước khi khôi phục tay ☐ Log ERROR `chưa xong, cần xử lý thủ công` ☐ Đã bật lại service và đặt `PAYMENT_DELAY_MS=0`

---

## 8. Chạy tự động bằng Collection Runner / Newman

Sau khi đã chạy tay và hiểu từng KB, chạy lại toàn bộ tự động để có bằng chứng nộp bài.

### 8.1. Collection Runner (Postman)

1. Chuột phải vào collection → **Run collection**.
2. **Bỏ chọn** thư mục `Thủ công (không chạy bằng Runner)`.
3. Giữ nguyên thứ tự: `0 → KB1 → KB2 → KB3 → KB4 → KB5 → KB7` (KB7 cần `kb1OrderId`, `kb2OrderId` do KB1, KB2 sinh ra).
4. Bấm **Run** → tất cả test phải **Passed**.
5. Chụp màn hình kết quả Runner (TODO-11).

> Không cần reset trước khi chạy Runner: các test so sánh **độ chênh lệch** với snapshot. Tuy vậy, mỗi lần chạy sẽ trừ thêm 2 P1 và 2000 của C1 (KB1) – C1 bắt đầu với 5000 nên chỉ đủ cho **2 lần** KB1. Nếu đã chạy tay KB1 → KB8 rồi chạy Runner (C1 còn 1000), lần chạy Runner **tiếp theo** KB1 sẽ fail vì 402. Khi đó hãy reset (mục 4).

### 8.2. Newman (dòng lệnh, tuỳ chọn)

```powershell
npm install -g newman
cd slot_13/saga-demo
newman run Saga_Postman_Collection.json `
  --folder "0 - Kiểm tra service" --folder "KB1 - Happy path" `
  --folder "KB2 - Thanh toán thất bại (402)" --folder "KB3 - Hết hàng (409)" `
  --folder "KB4 - Sản phẩm không tồn tại (404 inventory)" --folder "KB5 - Ví không tồn tại (404 payment)" `
  --folder "KB7 - Idempotent (gọi lặp lại)"
```

Cuối output, bảng tổng kết phải có cột `failed` = **0**.

---

## 9. Mẫu ghi lại kết quả test

Sao chép bảng này vào báo cáo (Word) và điền sau mỗi KB. Đính kèm ảnh chụp: response Postman, tab Test Results, saga-logs, dòng log quan trọng.

| KB | Ngày test | Order id | `status / sagaStatus` thực tế | `failureReason` thực tế | Thứ tự saga log thực tế | Kho / ví sau KB | Log quan trọng | Kết quả (PASS/FAIL) | Ghi chú |
|---|---|---|---|---|---|---|---|---|---|
| KB1 | | | | | | | | | |
| KB2 | | | | | | | | | |
| KB3 | | | | | | | | | |
| KB4 | | | | | | | | | |
| KB5 | | | | | | | | | |
| KB6 | | | | | | | | | |
| KB7 | | – | – | – | – | | | | |
| KB8 | | | | | | | | | |

Gợi ý chọn ảnh chụp cho mỗi KB:
1. Response của `2. Đặt hàng` (thấy `status`, `sagaStatus`, `failureReason`).
2. Tab **Test Results** của `3. Kiểm tra kho & ví` (thấy dòng `Tồn kho P1: 8 -> 8`…).
3. Response của `4. Saga logs`.
4. Terminal log (inventory / payment / order) có dòng tương ứng ở mục 5 – lớp 4.

---

## 10. Xử lý sự cố thường gặp khi test

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `docker version` báo `failed to connect to the docker API` | Docker Desktop chưa mở | Mở Docker Desktop, chờ biểu tượng chuyển xanh |
| `saga-sqlserver` tự tắt / không healthy | Docker thiếu RAM | Settings → Resources → cấp ≥ 4 GB |
| Postman: `Could not send request` | Dùng Postman web, hoặc service chưa start xong | Dùng Postman Desktop; chờ log `Started ...` |
| `failureReason` chỉ là `402 PAYMENT_REQUIRED` không có message | Thiếu `spring.web.error.include-message=always` | Thêm vào `application.properties` của inventory/payment |
| Test KB1 fail: tồn kho / số dư không đủ | Chạy Runner quá nhiều lần | Reset dữ liệu (mục 4) |
| KB7 lỗi vì `kb1OrderId` rỗng | Chạy KB7 trước KB1/KB2 | Chạy đúng thứ tự |
| KB6 / KB8 `3. Kiểm tra` lỗi | Chưa bật lại service trước khi kiểm tra | `docker compose start ...` rồi chờ `Started` |
| KB8 ra `CANCELLED / COMPENSATED` thay vì `COMPENSATING` | Tắt service quá muộn (sau 15 giây), hoặc chỉ tắt payment | Chuẩn bị sẵn lệnh, Enter ngay khi thấy log `[DEMO] Chờ`; tắt **cả hai** service |
| KB8 không "treo" 15 giây | Biến `PAYMENT_DELAY_MS` chưa vào container | Chạy lại `$env:PAYMENT_DELAY_MS=15000; docker compose up -d order-service` trong **cùng** cửa sổ PowerShell |
| Sau KB8 không tìm thấy log `Compensation cho order=7 chưa xong` | `docker compose up -d order-service` đã tạo lại container, log cũ mất | Xem log **trước** khi khởi động lại order-service (bước 7 của KB8) |
| Sau KB8, các order mới đều chậm 15 giây | Quên tắt độ trễ | `$env:PAYMENT_DELAY_MS=0; docker compose up -d order-service` |
| Order id không khớp tài liệu | Chưa reset hoặc chạy khác thứ tự | Bình thường – so sánh theo id thực tế và độ chênh lệch |
| `Connection refused` khi order-service chạy trong Docker | Trỏ `localhost` thay vì tên service | Kiểm tra biến `INVENTORY_URL`, `PAYMENT_URL` trong `docker-compose.yml` |

---

## 11. Câu hỏi tự kiểm tra sau khi test

Trả lời được các câu sau nghĩa là bạn đã hiểu, không chỉ "chạy được":

1. Ở KB2, vì sao response vẫn là **HTTP 200** dù Saga thất bại?
2. Ở KB2, vì sao có `release` mà không có `refund`? Ở trường hợp nào thì `refund` mới được gọi? (gợi ý: EXT-1)
3. Ở KB3, vì sao saga log không có dòng `COMPENSATION`?
4. Ở KB6, lỗi mạng và lỗi 402 khác nhau thế nào trong `extractReason`? Vì sao một khối `catch (RestClientException)` bắt được cả hai?
5. Ở KB7, nếu `release` **không** idempotent thì retry ở KB8 sẽ gây hậu quả gì?
6. Ở KB8, vì sao order giữ `status = PENDING` chứ không phải `CANCELLED`? Nếu code vẫn gọi `cancelOrder` thì sai ở đâu?
7. Nếu đặt `@Transactional` lên `placeOrder()`, KB2 có tự rollback kho trong MongoDB không? Vì sao?
8. Trong khoảng 15 giây chờ ở KB8, gọi `GET /api/orders` sẽ thấy order 7 ở trạng thái gì? Đây là minh hoạ cho khái niệm nào (Slide 6, 21)?
