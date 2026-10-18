# H-003 Varış V2 — 250 maddelik yürütme kontrol listesi

> **Statü:** plan önerisi / `PLANNED`, uygulama kabulü değildir. **Kaynak:** 2026-10-14 kullanıcı talebi. **Baz:** L0 `H003-IDEA-REV-003`, mevcut L1–L5 planı ve `nazofobi-gpt/arrival-alarm-android-v2` main `d5e1092df616164aa15054b2b9e4dc50cd5f1e9c`.
> **Otorite:** Bu liste mevcut canonical `Görevler` G-ID’leri ve L5 `Registry` ile yarışmaz; işler uygulamaya alınmadan önce exact G-task/L5 bağı, güncel main/açık PR karşılaştırması ve kapsam tespiti yapılır. Liste maddeleri otomatik `DONE` değildir; her maddenin kanıtı olmadan kabul yoktur.
> **Durum ve yürütme:** `[ ]` = kontrol / geliştirme bekliyor; işlerin gerçek mevcut tamamlanma durumu yalnız canonical kaynaklardan okunur. Her uygulamada READ → COMPARE → MINIMAL PATCH → CI/API36 → READBACK → G-task+L5 reconcile. Her türlü canlıya alma ve saha kabulü ayrı release kapılarına tabidir.

## Önceliklendirme
Önce P0 güvenilirlik, gerçek Almanya verisi, sefer seçimleri, alarm ve release-blocking güvenlik. Bağımsız P1 UX, yerelleştirme ve performans işleri yalnız ilgili G/L5 bağı ve test kapılarıyla paralel yürütülür.

## Yönetişim ve izlenebilirlik (10 görev)

- [ ] **V250-001** · P0 · IDEA-H003-001/021/029: Main SHA, açık PR ve aktif G-task kanıtını dondur. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-002** · P0 · IDEA-H003-001/021/029: Her iş için L0 IDEA bağı ve owning G-task eşlemesini kaydet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-003** · P0 · IDEA-H003-001/021/029: L5 atom eşleşmesini tekil olarak doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-004** · P0 · IDEA-H003-001/021/029: G-254 tarihsel gereksinim kapsamasını uzlaştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-005** · P0 · IDEA-H003-001/021/029: Önceki V1 iptal kayıtlarını V2’den ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-006** · P0 · IDEA-H003-001/021/029: Çakışan açık PR dosya yollarını hesapla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-007** · P0 · IDEA-H003-001/021/029: Her patch için kök neden ve kabul kriterini yaz. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-008** · P0 · IDEA-H003-001/021/029: Her değişiklik için önceki/sonraki blob SHA kaydet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-009** · P0 · IDEA-H003-001/021/029: Yalnız exact-head CI ve API36 sonucunu kabul et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-010** · P0 · IDEA-H003-001/021/029: Başarı kanıtlarını L5 ve G-task kayıtlarına geri yaz. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## İlk kurulum ve veri indirme (10 görev)

- [ ] **V250-011** · P0 · IDEA-H003-013/014: İlk açılış veri hazır/değil durumunu doğru göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-012** · P0 · IDEA-H003-013/014: İndirme URL ve yanıt türü doğrulamasını sağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-013** · P0 · IDEA-H003-013/014: İndirmede bağlantı kesilmesini yeniden üret. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-014** · P0 · IDEA-H003-013/014: Kısmi dosyaların atomik aktivasyonunu engelle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-015** · P0 · IDEA-H003-013/014: Tekrar deneme ve iptal işlevini bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-016** · P0 · IDEA-H003-013/014: Disk dolu durumunda eski geçerli veriyi koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-017** · P0 · IDEA-H003-013/014: Checksum/manifest uyumsuzluğunu reddet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-018** · P0 · IDEA-H003-013/014: İndirme ilerlemesini erişilebilir göstergede sun. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-019** · P0 · IDEA-H003-013/014: Uygulamadan çıkıp dönünce yüklemeyi güvenle toparla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-020** · P0 · IDEA-H003-013/014: İlk kurulumdan etkin paket sorgusuna uçtan uca test ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## GTFS statik veri bütünlüğü (10 görev)

- [ ] **V250-021** · P0 · IDEA-H003-003/005: Almanya kaynağındaki GTFS dosya şemasını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-022** · P0 · IDEA-H003-003/005: Feed timezone ve service_date yorumunu test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-023** · P0 · IDEA-H003-003/005: Calendar/calendar_dates birleşimini sınır değerlerle test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-024** · P0 · IDEA-H003-003/005: Stop_times sırası ve stop_sequence tekilliğini denetle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-025** · P0 · IDEA-H003-003/005: 24:00 sonrası GTFS saatlerini doğru yorumla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-026** · P0 · IDEA-H003-003/005: Parent station ve platform referanslarını koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-027** · P0 · IDEA-H003-003/005: Feed kimliklerini sağlayıcı kapsamıyla ayrıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-028** · P0 · IDEA-H003-003/005: Import sırasında bozuk satırları güvenli raporla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-029** · P0 · IDEA-H003-003/005: Bozuk sürümde old-good veri setini koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-030** · P0 · IDEA-H003-003/005: Gerçek feed’den seçilmiş sefer sorgusunu kanıtla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Durak bulma ve arama (10 görev)

- [ ] **V250-031** · P0 · IDEA-H003-003/012: Paket aktivasyonunda arama indeksini yenile. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-032** · P0 · IDEA-H003-003/012: Lohne ve kısmi ad aramalarını gerçek veriyle doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-033** · P0 · IDEA-H003-003/012: Unicode büyük/küçük harf normalizasyonunu test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-034** · P0 · IDEA-H003-003/012: Yakın durak sorgusunu aktif indeksle eşitle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-035** · P0 · IDEA-H003-003/012: Durak sağlayıcı kimliği çakışmalarını ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-036** · P0 · IDEA-H003-003/012: Aramada iptal sonrası eski sonucu UI’a uygulama. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-037** · P0 · IDEA-H003-003/012: Adres/POI önerilerini gerçek kaynakla eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-038** · P0 · IDEA-H003-003/012: Durak detaylarını gerçek hat/kalkış verisine bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-039** · P0 · IDEA-H003-003/012: Yakındaki sonuçları doğruluk ve mesafe ile sırala. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-040** · P0 · IDEA-H003-003/012: Arama listesini TalkBack gezinmesiyle test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Gerçek zamanlı veriler (10 görev)

- [ ] **V250-041** · P0 · IDEA-H003-003: Scheduled ve realtime timestamp kaynaklarını ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-042** · P0 · IDEA-H003-003: Gecikme bilgisini ilgili trip/service_date ile eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-043** · P0 · IDEA-H003-003: Platform değişikliklerini anlamlı fark olarak göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-044** · P0 · IDEA-H003-003: İptal edilmiş kalkışları çift saymadan listele. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-045** · P0 · IDEA-H003-003: Service alert mesajlarını doğru hatlara bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-046** · P0 · IDEA-H003-003: Realtime yoksa statik veriye anlaşılır biçimde dön. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-047** · P0 · IDEA-H003-003: Eski realtime kaydını stale etiketiyle göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-048** · P0 · IDEA-H003-003: Out-of-order güncellemelerde en yeni kanıtı koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-049** · P0 · IDEA-H003-003: Canlı araç konumu varsa attribution ile sun. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-050** · P0 · IDEA-H003-003: Provider gerçek yanıtlarıyla regresyon fixture’ı doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Sağlayıcı dayanıklılığı (10 görev)

- [ ] **V250-051** · P0 · IDEA-H003-003/013: İstek zaman aşımı bütçesini sınırla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-052** · P0 · IDEA-H003-003/013: Yeniden denemelerde yalnız idempotent isteği tekrar et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-053** · P0 · IDEA-H003-003/013: İptal edilen istekte gerçek ağ başlatma. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-054** · P0 · IDEA-H003-003/013: Rate limit yanıtını saygılı backoff ile işle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-055** · P0 · IDEA-H003-003/013: Circuit breaker durum geçişlerini test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-056** · P0 · IDEA-H003-003/013: Provider kimliğini fallback gruplamada koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-057** · P0 · IDEA-H003-003/013: Ağ hatası ile veri yok durumunu ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-058** · P0 · IDEA-H003-003/013: Provider değişirken kaynak provenance’ını göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-059** · P0 · IDEA-H003-003/013: Yanlış content-type ve bozuk gövdeyi reddet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-060** · P0 · IDEA-H003-003/013: Sağlıklı sağlayıcıya dönüşü ve cache toparlanmasını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Başlangıç ve varış seçimi (10 görev)

- [ ] **V250-061** · P0 · IDEA-H003-003/012: İzinli cihaz konumunu başlangıç önerisine bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-062** · P0 · IDEA-H003-003/012: Konum izni reddinde manuel başlangıç sun. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-063** · P0 · IDEA-H003-003/012: Durak listesinden başlangıç seçimini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-064** · P0 · IDEA-H003-003/012: Adres listesinden varış seçimini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-065** · P0 · IDEA-H003-003/012: POI sonucunu koordinat ve adla kaydet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-066** · P0 · IDEA-H003-003/012: Harita pininden varış seçimine doğrulama ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-067** · P0 · IDEA-H003-003/012: Başlangıç ve varış aynıysa anlaşılır hata göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-068** · P0 · IDEA-H003-003/012: Gidiş-yön değişimini rota yeniden hesaplamaya bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-069** · P0 · IDEA-H003-003/012: Arama iptalini konum listener lifecycle’ıyla eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-070** · P0 · IDEA-H003-003/012: Seçimi ekran dönüşünde geri yükle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Rota motoru (10 görev)

- [ ] **V250-071** · P0 · IDEA-H003-003/032: Mevcut zamana uygun sefer adaylarını üret. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-072** · P0 · IDEA-H003-003/032: Service_date ve timezone sınırını koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-073** · P0 · IDEA-H003-003/032: Doğrudan rota alternatiflerini gerçek feed’den getir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-074** · P0 · IDEA-H003-003/032: Tek aktarma için yürüyüş ve bekleme süresini hesapla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-075** · P0 · IDEA-H003-003/032: Altı alternatifin benzersizliğini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-076** · P0 · IDEA-H003-003/032: Yalnız aktarmasız filtre tercihini kalıcı uygula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-077** · P0 · IDEA-H003-003/032: İmkansız rotada yanlış öneri sunma. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-078** · P0 · IDEA-H003-003/032: Son varış durağını uzun rota önizlemesinde koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-079** · P0 · IDEA-H003-003/032: Sefer gecikince rota tahminini güncelle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-080** · P0 · IDEA-H003-003/032: Seçilen alternatifi journey state’e güvenli bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Sefer-öncelikli seçim (10 görev)

- [ ] **V250-081** · P0 · IDEA-H003-032: Hat/servis aramasından gerçek trip örneği seç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-082** · P0 · IDEA-H003-032: TripInstance ID ile servis tarihini birlikte taşı. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-083** · P0 · IDEA-H003-032: Seçilen seferin yönünü UI’da göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-084** · P0 · IDEA-H003-032: Seferin sıralı duraklarını gerçek kaynaktan yükle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-085** · P0 · IDEA-H003-032: Durak listesinden başlangıç durağını seç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-086** · P0 · IDEA-H003-032: Durak listesinden varış durağını seç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-087** · P0 · IDEA-H003-032: Opsiyonel alarm hedef durağını ayrıca seç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-088** · P0 · IDEA-H003-032: Başlangıçtan önceki varış seçimini reddet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-089** · P0 · IDEA-H003-032: Realtime değişince seçili trip’i doğru eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-090** · P0 · IDEA-H003-032: Sefer seçiminden GPS alarmını kurmaya E2E test yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Durak sırası ve ilerleme (10 görev)

- [ ] **V250-091** · P0 · IDEA-H003-003/032: Durak önizlemesinde gerçek ilk ve son durağı koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-092** · P0 · IDEA-H003-003/032: 16 ve 17 durak kenar durumlarını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-093** · P0 · IDEA-H003-003/032: 64 durak için sıralamayı bozmadan genişlet/daralt. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-094** · P0 · IDEA-H003-003/032: Orijinal stop_sequence indekslerini koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-095** · P0 · IDEA-H003-003/032: Atlanan ara durak sayısını doğru etiketle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-096** · P0 · IDEA-H003-003/032: Geçilen ve sıradaki durağı ayrı göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-097** · P0 · IDEA-H003-003/032: Rota dışı GPS verisini sessiz durak geçişi sayma. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-098** · P0 · IDEA-H003-003/032: Trip yönü değişince eski ilerlemeyi temizle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-099** · P0 · IDEA-H003-003/032: Recreation sonrasında seçili durağı koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-100** · P0 · IDEA-H003-003/032: Uzun durak listesinde TalkBack sırasını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Cihaz konumu (10 görev)

- [ ] **V250-101** · P0 · IDEA-H003-002/004: Gerçek fused-location update akışını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-102** · P0 · IDEA-H003-002/004: İlk fix gelmeden yanlış varış tetikleme. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-103** · P0 · IDEA-H003-002/004: GPS doğruluğunu yaklaşım kararına kat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-104** · P0 · IDEA-H003-002/004: Eski konum sample’ını reddet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-105** · P0 · IDEA-H003-002/004: Fake/mock location’ı production kanıtı sayma. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-106** · P0 · IDEA-H003-002/004: Konum listener iptal ve restart durumlarını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-107** · P0 · IDEA-H003-002/004: Arka plan FGS konum izni akışını tamamla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-108** · P0 · IDEA-H003-002/004: Düşük güç koşulunda örnekleme davranışını ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-109** · P0 · IDEA-H003-002/004: Konum kapatılınca kullanıcıya açıklayıcı durum göster. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-110** · P0 · IDEA-H003-002/004: Transit olmadan taksi yolculuğunda hedef alarmını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Alarm güvenilirliği (10 görev)

- [ ] **V250-111** · P0 · IDEA-H003-002/004: Alarmı yalnız kullanıcı onayıyla armed yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-112** · P0 · IDEA-H003-002/004: Hedef yaklaşım eşiği değişimini deterministik işle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-113** · P0 · IDEA-H003-002/004: GPS sıçramasında false-positive engelle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-114** · P0 · IDEA-H003-002/004: Aynı hedefte alarmı en fazla bir kez ateşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-115** · P0 · IDEA-H003-002/004: ALARMED durumunu yan etkilerden önce kalıcı yaz. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-116** · P0 · IDEA-H003-002/004: Process death sonrası alarm durumunu toparla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-117** · P0 · IDEA-H003-002/004: Kilit ekranında devam eden sefer takibini test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-118** · P0 · IDEA-H003-002/004: Kullanıcı iptalinde servis ve ses kaynaklarını bırak. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-119** · P0 · IDEA-H003-002/004: Gerçek hedef adıyla TTS alarmını çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-120** · P0 · IDEA-H003-002/004: Offline seyahatte alarmı ağdan bağımsız doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## TTS ve bildirimler (10 görev)

- [ ] **V250-121** · P1 · IDEA-H003-004: Durak adını gerçekte seçili feed’den oku. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-122** · P1 · IDEA-H003-004: Durak geçişi anonslarını sırayla sun. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-123** · P1 · IDEA-H003-004: Tekrarlanan realtime anonslarını dedupe et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-124** · P1 · IDEA-H003-004: Bildirim kanalı ve önem seviyesini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-125** · P1 · IDEA-H003-004: TTS init başarısızlığında cihaz sesine fallback yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-126** · P1 · IDEA-H003-004: TTS dilinin durak adı telaffuzunu test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-127** · P1 · IDEA-H003-004: Alarm ile bilgilendirme sesini önceliklendir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-128** · P1 · IDEA-H003-004: Notification permission reddini güvenli ele al. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-129** · P1 · IDEA-H003-004: Servis arızası bildirimini anlamlı farkla sınırla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-130** · P1 · IDEA-H003-004: Screen-off durumda gerçek TTS çıktısını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Bluetooth ve ses yönlendirme (10 görev)

- [ ] **V250-131** · P1 · IDEA-H003-004: Bluetooth bağlanma/kopma olayını izle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-132** · P1 · IDEA-H003-004: Bağlı ses çıkışına uygun anonsu seç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-133** · P1 · IDEA-H003-004: AudioFocus istek/bırakma lifecycle’ını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-134** · P1 · IDEA-H003-004: Çağrı geldiğinde anons kesintisini güvenli işle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-135** · P1 · IDEA-H003-004: Medya ses kısılınca alarmı davranış olarak doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-136** · P1 · IDEA-H003-004: Kulaklık çıkarılınca device-speaker fallback yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-137** · P1 · IDEA-H003-004: Anons kuyruğunda hedef alarmını öne al. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-138** · P1 · IDEA-H003-004: Ses çıkışı değişiminde çift TTS üretme. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-139** · P1 · IDEA-H003-004: Bluetooth iznini OS sürümüne göre doğru iste. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-140** · P1 · IDEA-H003-004: Gerçek araç kulaklığıyla fiziksel ses denemesi yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Çevrimdışı süreklilik (10 görev)

- [ ] **V250-141** · P0 · IDEA-H003-002/003/014: Cache yüklemesinde yanlış typed preference’a dayan. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-142** · P0 · IDEA-H003-002/003/014: Bozuk cache dosyasını silmeden fail-closed işle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-143** · P0 · IDEA-H003-002/003/014: Sağlam cache kaydını yaz/geri yükle testini ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-144** · P0 · IDEA-H003-002/003/014: Offline modda önceden indirilen durakları bul. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-145** · P0 · IDEA-H003-002/003/014: Offline rotayı timetable provenance’ı ile etiketle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-146** · P0 · IDEA-H003-002/003/014: Offline modda son bilinen realtime’ı canlı gösterme. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-147** · P0 · IDEA-H003-002/003/014: Geçerli paketle ağsız alarmı çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-148** · P0 · IDEA-H003-002/003/014: Cache versiyon geçişlerini güvenli yönet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-149** · P0 · IDEA-H003-002/003/014: Eski paketten yenisine atomik geçişi test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-150** · P0 · IDEA-H003-002/003/014: Bağlantı gelince duruma zarar vermeden senkronize et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Journey state dayanıklılığı (10 görev)

- [ ] **V250-151** · P0 · IDEA-H003-002/032: Bozuk kalıcı progress okumasında crash’i önle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-152** · P0 · IDEA-H003-002/032: Sağlıklı progress snapshot’ını koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-153** · P0 · IDEA-H003-002/032: State restoration’da IDLE fail-closed davranışını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-154** · P0 · IDEA-H003-002/032: Kullanıcı arm etmeden alarmı otomatik açma. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-155** · P0 · IDEA-H003-002/032: Aynı seferi birden çok process restore’a dayan. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-156** · P0 · IDEA-H003-002/032: Yolculuk kimliğini stop sequence’e bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-157** · P0 · IDEA-H003-002/032: Service_date değişince eski rota durumunu kapat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-158** · P0 · IDEA-H003-002/032: Stale konum ile güncel state’i birbirinden ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-159** · P0 · IDEA-H003-002/032: Journey değişince eski bildirimleri kapat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-160** · P0 · IDEA-H003-002/032: Yeniden başlatmadan yeni geçerli sefer kurabilmeyi test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Görsel sistem ve ekranlar (10 görev)

- [ ] **V250-161** · P1 · IDEA-H003-007/008/010/011: Figma görsel kaynağı ve ekran kimliklerini uzlaştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-162** · P1 · IDEA-H003-007/008/010/011: Onboarding ekranını tasarım kanıtıyla eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-163** · P1 · IDEA-H003-007/008/010/011: Home ekranını gerçek GPS/data state’e bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-164** · P1 · IDEA-H003-007/008/010/011: Search ekranını list-first görsel sistemle eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-165** · P1 · IDEA-H003-007/008/010/011: Route sonuç kartlarını gerçek varyantlarla tasarla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-166** · P1 · IDEA-H003-007/008/010/011: Journey detail ekranında tüm durakları okunur kıl. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-167** · P1 · IDEA-H003-007/008/010/011: Live alarm ekranında sağlık ve hedef durumunu vurgula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-168** · P1 · IDEA-H003-007/008/010/011: Station departures ekranını tutarlı kartlarla birleştir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-169** · P1 · IDEA-H003-007/008/010/011: Settings ekranını liste/radio seçim modeliyle kur. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-170** · P1 · IDEA-H003-007/008/010/011: Fiziksel screenshot farklarını tasarım kaynağına göre kapat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Erişilebilirlik (10 görev)

- [ ] **V250-171** · P1 · IDEA-H003-004/007/012: TalkBack ekran başlık sırasını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-172** · P1 · IDEA-H003-004/007/012: Durak listesinde semantik seçim durumunu açıkla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-173** · P1 · IDEA-H003-004/007/012: Rota kartlarında kalkış/varış sesli özetini ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-174** · P1 · IDEA-H003-004/007/012: Dokunma hedefi minimum boyutunu denetle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-175** · P1 · IDEA-H003-004/007/012: Büyük yazıda kritik buton taşmalarını önle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-176** · P1 · IDEA-H003-004/007/012: Ekran kontrastını karanlık/açık modda ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-177** · P1 · IDEA-H003-004/007/012: Klavye ile liste seçim akışını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-178** · P1 · IDEA-H003-004/007/012: İlerleme göstergesinde erişilebilir açıklama sun. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-179** · P1 · IDEA-H003-004/007/012: Tekrarlayan TTS ile TalkBack çakışmasını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-180** · P1 · IDEA-H003-004/007/012: Yüksek ölçekli fontta alarm iptalini erişilebilir tut. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Yerelleştirme (10 görev)

- [ ] **V250-181** · P1 · IDEA-H003-004/012: TR/EN/DE kritik akış metinlerini eksiksiz karşılaştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-182** · P1 · IDEA-H003-004/012: Dil seçimini list-first ve kalıcı yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-183** · P1 · IDEA-H003-004/012: Rota tarih/saatini yerel timezone’da biçimlendir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-184** · P1 · IDEA-H003-004/012: Service alert kaynak metni ve çevirisini ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-185** · P1 · IDEA-H003-004/012: Almanca uzun durak adlarının taşmasını önle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-186** · P1 · IDEA-H003-004/012: Genel hata mesajlarında teknik kodları gizle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-187** · P1 · IDEA-H003-004/012: Rota filtresi ve durak listesi metinlerini yerelleştir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-188** · P1 · IDEA-H003-004/012: TTS locale fallback sırasını belirle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-189** · P1 · IDEA-H003-004/012: Erişilebilirlik açıklamalarını üç dilde doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-190** · P1 · IDEA-H003-004/012: Locale değişiminde journey/alarm state’i koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Harici ChatGPT connector (10 görev)

- [ ] **V250-191** · P0 · IDEA-H003-015/016: Android’e gömülü LLM/API key olmadığını denetle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-192** · P0 · IDEA-H003-015/016: Canonical journey state için salt-okuma şeması tanımla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-193** · P0 · IDEA-H003-015/016: Harici gateway auth/device binding’i gerçek kaynakla kur. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-194** · P0 · IDEA-H003-015/016: Typed komutların allowlist’ini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-195** · P0 · IDEA-H003-015/016: Alarm kurmayı açık kullanıcı onayına bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-196** · P0 · IDEA-H003-015/016: Idempotency claim key biçimini katı doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-197** · P0 · IDEA-H003-015/016: Durable journal JSON primitive tiplerini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-198** · P0 · IDEA-H003-015/016: Replay fingerprint eşleşmesini fail-closed koru. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-199** · P0 · IDEA-H003-015/016: Komut receipt ve state version readback’ini test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-200** · P0 · IDEA-H003-015/016: Fiziksel cihazla ChatGPT→gateway→uygulama E2E kanıtla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Güvenlik ve mahremiyet (10 görev)

- [ ] **V250-201** · P0 · IDEA-H003-002/015/016: Konum verisinin saklama amacını envanterle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-202** · P0 · IDEA-H003-002/015/016: Konum paylaşımını izin/kapalı durumuyla eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-203** · P0 · IDEA-H003-002/015/016: Loglarda secret veya hassas koordinat sızıntısını denetle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-204** · P0 · IDEA-H003-002/015/016: Gateway token doğrulamasını fail-closed test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-205** · P0 · IDEA-H003-002/015/016: Komutların yetkili cihazla sınırlanmasını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-206** · P0 · IDEA-H003-002/015/016: Malformed JSON ve boyut limitlerini uygula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-207** · P0 · IDEA-H003-002/015/016: Local depolamada hassas kayıt temizleme davranışını test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-208** · P0 · IDEA-H003-002/015/016: Permission geri alınırsa servisleri kapat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-209** · P0 · IDEA-H003-002/015/016: Play Data Safety beyanını gerçek kod davranışıyla eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-210** · P0 · IDEA-H003-002/015/016: Release build’de debug ağ/gizli kapı kalmadığını doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## JVM ve alan testi (10 görev)

- [ ] **V250-211** · P0 · IDEA-H003-005/006: GTFS service_date sınır koşulu regresyonunu çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-212** · P0 · IDEA-H003-005/006: Duplicate cancellation gruplama regresyonu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-213** · P0 · IDEA-H003-005/006: Provider fallback identity regresyonu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-214** · P0 · IDEA-H003-005/006: Journey stop preview 0/16/17/64 regresyonunu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-215** · P0 · IDEA-H003-005/006: Wrong-type offline cache load regresyonunu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-216** · P0 · IDEA-H003-005/006: Corrupt progress restore regresyonunu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-217** · P0 · IDEA-H003-005/006: Idempotency claim primitive type regresyonunu ekle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-218** · P0 · IDEA-H003-005/006: Route filter persistence regresyonunu çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-219** · P0 · IDEA-H003-005/006: Stale realtime/hysteresis regresyonlarını çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-220** · P0 · IDEA-H003-005/006: Test fixture’larını production kaynaklardan ayır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Android API36 entegrasyonu (10 görev)

- [ ] **V250-221** · P0 · IDEA-H003-002/004/006: Her değişiklikte exact-head Android CI çalıştır. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-222** · P0 · IDEA-H003-002/004/006: API36 emulator testini aynı commit SHA ile eşle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-223** · P0 · IDEA-H003-002/004/006: FGS konum izni gerçek Android lifecycle’ında test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-224** · P0 · IDEA-H003-002/004/006: Device restart sonrası armed state’i doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-225** · P0 · IDEA-H003-002/004/006: Screen-off konum izlemeyi instrument et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-226** · P0 · IDEA-H003-002/004/006: TTS init ve alarm yan etkilerini instrument et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-227** · P0 · IDEA-H003-002/004/006: Cache corruption storage regression’ını instrument et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-228** · P0 · IDEA-H003-002/004/006: Notification deny/allow geçişini instrument et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-229** · P0 · IDEA-H003-002/004/006: Concurrent trip seçimi ve cancellation race’ini test et. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-230** · P0 · IDEA-H003-002/004/006: Android CI sonuçlarını L5’e run/job ID ile bağla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Performans ve kaynaklar (10 görev)

- [ ] **V250-231** · P1 · IDEA-H003-001/021: Arama indeks hazırken sorgu latency baseline’ı ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-232** · P1 · IDEA-H003-001/021: Rota aday üretim süresi ve doğruluğunu ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-233** · P1 · IDEA-H003-001/021: İlk kurulum download/index zamanını ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-234** · P1 · IDEA-H003-001/021: Bellek kullanımını büyük Germany feed’de ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-235** · P1 · IDEA-H003-001/021: GPS tracking pil tüketimini gerçek cihazda ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-236** · P1 · IDEA-H003-001/021: Network request tekrar oranını ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-237** · P1 · IDEA-H003-001/021: Provider backoff’un çağrı sayısını azaltmasını ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-238** · P1 · IDEA-H003-001/021: TTS kuyruk gecikmesini ölç. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-239** · P1 · IDEA-H003-001/021: Crash-free restore senaryolarını örneklemle. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-240** · P1 · IDEA-H003-001/021: Ölçülmeyen enerji verisini UNKNOWN olarak kaydet. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

## Saha, sürüm ve Play kabulü (10 görev)

- [ ] **V250-241** · P0 · IDEA-H003-005/006/017/018: Tüm aktif L5 release-blocking atomlarını kanıtla kapat. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-242** · P0 · IDEA-H003-005/006/017/018: GATE-001 kapsam/lineage kontrolünü bitir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-243** · P0 · IDEA-H003-005/006/017/018: GATE-003 onaylı görsel kaynak karşılaştırmasını bitir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-244** · P0 · IDEA-H003-005/006/017/018: GATE-004 exact-head otomasyon kapısını bitir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-245** · P0 · IDEA-H003-005/006/017/018: GATE-006 güvenlik ve Play dossier’ini bitir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-246** · P0 · IDEA-H003-005/006/017/018: GATE-007 gerçek harici connector turunu bitir. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-247** · P0 · IDEA-H003-005/006/017/018: GATE-011 sefer-durak seçimi on paketini doğrula. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-248** · P0 · IDEA-H003-005/006/017/018: Lohne ve farklı Alman şehirlerinde gerçek cihaz E2E yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-249** · P0 · IDEA-H003-005/006/017/018: GATE-009 tek RC saha kabulünü imzalı kanıtla. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.
- [ ] **V250-250** · P0 · IDEA-H003-005/006/017/018: GATE-010 davranış dondurma sonrası Play preflight yap. **Kabul:** ilgili gerçek ürün akışında tekrarlanabilir test/ölçüm, exact commit veya fiziksel kanıt ve canonical G/L5 readback.

**Kontrol toplamı:** 250 görev; 25 iş akışı × 10 görev.
