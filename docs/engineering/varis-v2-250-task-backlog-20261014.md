# Varış V2 — 250 Maddelik Mühendislik İş Listesi

**Oluşturulma:** 2026-10-14 (Europe/Berlin)  
**Repo:** `nazofobi-gpt/arrival-alarm-android-v2`  
**İlk doğrulanan base/main:** `d5e1092df616164aa15054b2b9e4dc50cd5f1e9c`  
**Amaç:** Açık H-003/V2 kapsamını 250 küçük, kanıtlanabilir mühendislik işi olarak ele almak; mevcut canonical Görevler ve L5 atom kayıtlarını yeniden numaralandırmadan, onları ikame etmeden planlamak.

> **Durum kuralı:** `[ ]` aday iş demektir, bitmiş veya sahiplenilmiş iş değildir. Bir işin zaten mevcut main'de/PR'da tamamlandığı ortaya çıkarsa önce dedup ve exact-head doğrulaması yapılır. Başarılı CI, gerçek cihaz kabulü yerine geçmez. G-136/G-138 kapıları geçilmeden 'release-ready' denmez.

## Yürütme politikası

- Önce güncel main/branch/açık PR ve canonical G-task/L5 kanıtı kontrol edilir. Eski V1 G-009 yürütülmez.
- `READ → root-cause/evidence → minimal change → regression test → exact-head CI/API36 → readback` izlenir; tahmini/refactor yamaları yapılmaz.
- Alan/başlık G görevleri için yönlendirme niteliğindedir; aynı G altında paralel PR çakışması varsa dosyaya yazmadan önce yeniden karşılaştırılır.
- Her alt iş tamamlanırken commit/PR/run/fixture/fiziksel kabul linki ilgili canonical task ve kanıt kaydına işlenir. Test/cihaz altyapısı yoksa VERIFIED iddiası yapılmaz.

## 01 · Ana dal/CI doğruluğu — G-173

- [ ] **V2-001** Android CI exact-HEAD SHA doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-002** API36 instrumentation exact-HEAD SHA doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-003** Eşzamanlı PR dosya çakışmalarını karşılaştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-004** Başarısız test ile altyapı hatasını ayır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-005** Gradle unit-test raporunu sakla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-006** Manifest lint sonuçlarını denetle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-007** Release derleme zinciri girdilerini sabitle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-008** Bağımlılık değişikliklerini ayrı incele. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-009** Birleştirme öncesi PR diff riskini değerlendir. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-010** Birleştirme sonrası main readback kanıtla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 02 · Almanya-geneli GTFS veri alımı — G-185

- [ ] **V2-011** GTFS ZIP indirimi zaman aşımı sınırını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-012** ZIP boyut ve CRC doğrulaması ekle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-013** Eksik stops.txt durumunu fail-closed ele al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-014** Eksik trips.txt durumunu fail-closed ele al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-015** Bozuk stop_times satırlarını güvenli reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-016** Takvim ve takvim-istisnası birlikte işlesin. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-017** Veri paketinin kaynak sürümünü kaydet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-018** İndeks takasından önce integrity gate çalıştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-019** Güncelleme başarısızsa eski paketi koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-020** Gerçek Almanya veri setiyle cihazda doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 03 · Durak/adres araması — G-168

- [ ] **V2-021** Lohne 'loh' prefiks sorgusunu gerçek indekste sınırla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-022** Umlaut ve ASCII eşlemesini sınır durumlarda doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-023** Aynı isimli farklı şehirleri ayırt et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-024** Durak ID ayrımını provider kimliğiyle koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-025** Arama sonuçlarını deterministik sırala. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-026** Geç gelen arama yanıtını yoksay. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-027** Arama iptalinde kaynakları serbest bırak. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-028** Paket aktivasyonunda arama indeksini yenile. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-029** Offline aramada explicit veri durumunu göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-030** Gerçek cihaz klavye ve IME etkileşimini doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 04 · Yakındaki duraklar — G-170

- [ ] **V2-031** Konum izinli/izinsiz durumları ayır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-032** Geçersiz koordinatları ranking öncesi reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-033** En yakın durakları gerçek mesafeyle sıralandır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-034** Ana istasyon/platform tekilleştirme doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-035** Kısmi provider yanıtlarında kimlik karışmasını engelle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-036** Yakın durak aramasını iptal edilebilir kıl. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-037** Stale GPS fix'i kullanıcıya belirt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-038** Harita ve liste aynı stop id'yi kullansın. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-039** Yakındaki kalkış bağlantısını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-040** Lohne saha koordinatlarında sonuçları karşılaştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 05 · Statik rota motoru — G-175

- [ ] **V2-041** Doğrudan güzergâhı gerçek stop_times üzerinden kur. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-042** Tek aktarmalı sefer adaylarını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-043** İmkansız bağlantıları reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-044** Minimum aktarma süresini uygula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-045** Güzergâh çıktısında gerçek durak sırasını koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-046** Başlangıç durağı seçimini güncel origin'e bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-047** Varış durağı seçimini güncel destination'a bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-048** Transfer-only sonuç filtrelerini doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-049** Aynı OD için deterministik alternatifleri göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-050** Almanya fixture + gerçek veri rota kabulünü yap. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 06 · Servis günü ve saat mantığı — G-179

- [ ] **V2-051** Gece yarısını geçen GTFS zamanını çöz. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-052** 24+ saat formatını sınırda doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-053** DST ileri geçişinde sefer günü ayrımı yap. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-054** DST geri geçişinde çift saat güvenli kalsın. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-055** Geçersiz UTC offset değerlerini reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-056** Geçersiz tarih-gün birleşimlerini reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-057** Farklı timezone kaynaklarını normalize et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-058** Varış-kalkış tersliğini reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-059** İki durak arasında ters kronolojiyi reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-060** Uzun yolculuk süresini dakika/saat olarak doğru göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 07 · Realtime GTFS-RT — G-176

- [ ] **V2-061** Germany-wide GTFS-RT kaynak erişimini doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-062** Payload metadata timestamp yaşını hesapla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-063** Gelecek tarihli snapshot'ı reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-064** Static trip ile realtime trip kimliğini eşle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-065** Delay değerlerinde signed aralığı doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-066** Cancelled trip bilgisini alarm motoruna taşı. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-067** ServiceAlert'i bağlı durakta göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-068** Platform değişikliğini provider-gated göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-069** Stale eşleşmeyi planlı sefer fallback'e düşür. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-070** Gerçek GTFS-RT feed API36 smoke çalıştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 08 · Provider dayanıklılığı — G-177/G-193

- [ ] **V2-071** Network timeout'u deterministic sınıflandır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-072** Retry backoff + üst sınırını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-073** User cancel sonrası HTTP I/O üretme. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-074** Circuit breaker recovery'yi doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-075** HTTP 304 cache revalidation'ı test et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-076** Kısmi DB enrichment sonucunu belirt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-077** Provider 429 backoff politikasını uygula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-078** Regional→nationwide fallback kimliğini koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-079** Offline provider hata metnini kullanılabilir göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-080** Retry telemetrisinde sır/konum sızıntısını engelle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 09 · Durak kalkış panoları — G-171

- [ ] **V2-081** Gerçek bir durakta sıradaki kalkışları getir. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-082** Aynı trenin duplicate panosunu engelle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-083** Cancelled trip'i doğru işaretle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-084** Platform bazlı durakları ana istasyonda grupla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-085** Scheduled ve realtime zamanını ayrı göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-086** Geçmiş kalkışları saklama. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-087** Yakındaki durağa tıklayınca panoyu aç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-088** Offline panoda veri zamanını açık göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-089** Boş pano ile hata panosunu ayırt et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-090** Ekran okuyucuya hat ve yön bilgisini duyur. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 10 · Rota alternatifleri — G-172

- [ ] **V2-091** Doğrudan ve aktarmalı seçenekleri ayrı göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-092** Erken/geç kalkış alternatiflerini listele. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-093** Gerçek departure değişikliğini aksiyona bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-094** Walking-leg bilgisini satır bazında göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-095** Seçilen alternatifin kimliğini koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-096** İptal edilmiş alternatifi seçilebilir yapma. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-097** İlgili ServiceAlert'i rotta görünür kıl. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-098** Provider-supported live vehicle varlığını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-099** Vehicle yoksa sahte işaretçi gösterme. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-100** Alternatif değiştirmede eski alarmı güvenli eşle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 11 · Seçili sefer kimliği — G-182

- [ ] **V2-101** TripInstance selection modelini sabitle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-102** Servis tarihini stop seçiminde sakla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-103** Seçili boarding stop'u trip sırasına göre doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-104** Destination stop trip sırasından sonra olmalı. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-105** Alarm target stop'u route ordering ile doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-106** Seçim restore'da static data ile yeniden eşleşsin. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-107** Stale/missing trip açık UNKNOWN dönsün. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-108** Offline restart selection state korusun. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-109** Trip switch önceki selection'ı atomik iptal etsin. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-110** Gerçek sefer dizisinde boarding/target cihaz kabulü. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 12 · Canlı yolculuk rehberi — G-180

- [ ] **V2-111** Yola çıkma öncesi hatırlatmayı doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-112** Durağa yürüme aşamasını göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-113** Boarding cue'yu kanıta bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-114** Previous/current/next stop aşamalarını ayır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-115** Kalan durak sayısını route order'dan türet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-116** Aktarma aşamasında hedef ve platformu koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-117** Get-off cue'yu doğrulanmış alarm durakta tetikle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-118** Son yürüme bacağını açık göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-119** Kaçan aktarmada reroute önerisini üret. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-120** Restart sonrası phantom progress oluşmadığını kanıtla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 13 · GPS tabanlı varış alarmı — G-181

- [ ] **V2-121** Koordinat doğruluğu eşiğini uygula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-122** Hedef yarıçapının hızla ilişkisini test et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-123** Konum sıçramasında false positive alarmı önle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-124** Offline GPS-only alarmı sürdür. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-125** Aynı hedef alarmını bir defa çal. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-126** Alarm tetiklendiğini side-effect öncesi persist et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-127** Alarm hedefi değişince eski trigger'ı sıfırla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-128** Hatalı restore state'i güvenle ele al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-129** Alarmı ekrandan ve bildirimden iptal et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-130** Gerçek yolculukta hedef yakınlığı saha kabulü. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 14 · Arka planda izleme — G-181

- [ ] **V2-131** User-started FGS başlatma iznini denetle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-132** Android 16 konum izin akışını denetle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-133** Uygulama minimize edilince takip sürsün. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-134** Screen-off sonrası takip state restore et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-135** Provider kapalıyken uyarı göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-136** Provider yeniden açılınca listener'ı toparla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-137** Service restart duplicate alarm üretmesin. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-138** Notification action idempotent olsun. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-139** Battery optimization koşullarını belgeye al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-140** API36 service lifecycle instrumentation çalıştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 15 · Durak TTS akışı — G-181

- [ ] **V2-141** Gerçek durak adını TTS'e bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-142** Tüm duraklar anons modunu doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-143** Aktarma+varış modunu doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-144** Yalnız varış modunu doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-145** Sessiz modu side-effect üretmesin. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-146** Dil değişiminde mevcut ses yeteneğini koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-147** Ekran kilidinde konuşmayı doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-148** TTS engine yokken açık fallback sun. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-149** Gecikme bildirimi konuşmasını deduplicate et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-150** Ses odağını anons sonrası bırak. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 16 · Bluetooth/ses çıkışı — G-181

- [ ] **V2-151** Bluetooth A2DP route değişimini izle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-152** Kablolu kulaklık geçişini izle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-153** Telefon speaker fallback'i doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-154** Audio focus transient loss'u ele al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-155** Ses cihazı kopunca alarmı kaybetme. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-156** Anons ile alarm arasındaki önceliği tanımla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-157** Arka planda seçili ses yolunu koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-158** Çoklu cihazlarda route tercih tutarlılığı. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-159** Mute tercihini route değişiminde koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-160** Fiziksel kulaklık/BT saha testi yürüt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 17 · Offline davranış ve önbellek — G-185

- [ ] **V2-161** İlk indirme önce çevrimdışı hazır gösterme. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-162** Offline cache sürüm metadata'sını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-163** Corrupt cache içeriğini güvenle reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-164** Bütünsel snapshot güncellemesi yap. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-165** Uygulama restart'ında index readiness doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-166** Offline search/route parity test et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-167** Cache güncellemesinde rollback yolunu doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-168** Yetersiz alan durumunda eski paketi koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-169** Ağ geri gelince realtime reconcile et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-170** Offline yalnız GPS alarmını garanti altına al. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 18 · Harita ve durak seçimi — G-184/G-187

- [ ] **V2-171** Harita pini koordinat geçerliliğini doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-172** Durak harita marker'ını gerçek ID'ye bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-173** Seçili marker ile destination alanını senkron tut. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-174** Harita viewport'una göre durakları sınırla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-175** GTFS trip shape çizgisini kaynak veriye bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-176** Şekil yoksa düz hat uydurma. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-177** Klavyeyle pin koordinatı seçimini doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-178** TalkBack marker açıklamasını denetle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-179** İzin yokken manuel harita piniyle devam et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-180** Gerçek route shape cihaz ekranında kabul et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 19 · Ana ekran/Compose ergonomisi — G-167

- [ ] **V2-181** Search CTA'yı ilk viewport'ta erişilebilir kıl. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-182** Yakındaki durakları kompakt listeyle sun. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-183** Bottom nav icon ve etiketlerini ayır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-184** Route card görsel hiyerarşisini düzelt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-185** Loading/error/empty durumlarını ayrı göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-186** Dokunma hedeflerini 48dp doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-187** Font scale 200% taşmaları düzelt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-188** Landscape/küçük ekranda kesilme engelle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-189** Dark/light tema kontrastını ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-190** Gerçek cihaz ekran görüntüsü karşılaştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 20 · Settings/readiness sinyalleri — G-173

- [ ] **V2-191** Local GTFS index health'i bağımsız göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-192** Search readiness'i gerçek sorgu ile doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-193** Route readiness'i gerçek çözümlemeyle doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-194** Realtime stale state'i göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-195** Connector configured/connected ayrımını göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-196** Overall READY yalnız temel akış sağlamsa göster. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-197** Download progress ve indexed state'i ayır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-198** Provider hata kodunu kullanıcı metnine çevir. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-199** Kişisel veri içermeyen diagnostic summary üret. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-200** Data hazır→Lohne arama saha duman testi. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 21 · Yerelleştirme/erişilebilirlik — G-173

- [ ] **V2-201** Türkçe UI metinleri doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-202** Almanca UI metinleri doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-203** İngilizce UI metinleri doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-204** Eksik resource anahtarlarını CI'da yakala. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-205** Malformed format placeholder'ı reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-206** TalkBack journey state'ini anlamlı oku. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-207** Ekran okuyucuya realtime/scheduled ayrımını ver. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-208** RTL/l10n icon yönünü bozma. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-209** Dil değişiminde selected trip state'i koru. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-210** API36 Compose erişilebilirlik smoke yürüt. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 22 · Harici ChatGPT connector — G-153/G-191

- [ ] **V2-211** AI/LLM runtime'ını Android dışı tut. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-212** Allowlist edilmiş typed action'ları doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-213** Current journey state read endpoint'ini sınırla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-214** Rota seçme komutunu explicit consent'e bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-215** Alarm kur/iptal idempotency key doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-216** Malformed durable journal'ı fail-closed yap. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-217** Boolean/int coercion girişlerini reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-218** OAuth callback origin/state doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-219** Gecikmiş connector command replay'ini engelle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-220** Android app-native onay akışını cihazda doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 23 · Gizlilik/güvenlik — G-191/G-193

- [ ] **V2-221** Android manifest background location kapsamını denetle. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-222** Loglardan credential ve token değerlerini çıkar. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-223** OAuth URL query secrets'ı reddet. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-224** Connector callback redirect güvenli listeyi uygula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-225** HTTP transport'ta header redaction test et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-226** GPS geçmişini gereksiz persist etme. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-227** Hata ekranında hassas endpoint sızdırma. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-228** Debug/release permission farkını test et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-229** Cihaz diagnostic export gizlilik kontrolünü yap. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-230** Güvenlik regresyon kapısını release'e bağla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 24 · Performans/verimlilik — G-190

- [ ] **V2-231** Cold start süresini cihazda ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-232** GTFS index ilk oluşturma süresini ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-233** Warm stop search latency ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-234** Nearby-stop lookup latency ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-235** Static route query üst sınırını ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-236** Realtime parse bellek tepe değerini ölç. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-237** Cache read/write hacmini karşılaştır. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-238** API isteği/sorgu sayısını fixture üzerinde say. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-239** Gerçek enerji Wh ölçülmüyorsa UNKNOWN yaz. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-240** Yalnız doğrulanmış kalite+verim avantajıyla optimize et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

## 25 · Saha kabul/Play yayın öncesi — G-136/G-138

- [ ] **V2-241** Almanya gerçek durak aramasını kabul et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-242** Lohne yakın durak sahada kabul et. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-243** Gerçek origin→destination rota akışını tamamla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-244** Seçili sefer/stop sırasını fiziksel doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-245** Ekran kilitli GPS alarmını sahada doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-246** Bluetooth/TTS sahada doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-247** Offline→online geçişi sahada doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-248** Servis uyarıları/realtime varsa provider kanıtla. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-249** Google Play izin/veri güvenliği formlarını doğrula. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.
- [ ] **V2-250** İnsan onayıyla release candidate ve rollout kapısını değerlendir. **Doğrulama:** ilgili kod, negatif/sınır testi, exact-head CI veya gerekli gerçek veri/cihaz kanıtı; mevcut PR ile dedup.

**Toplam:** 250 açık aday görev. **İlk uygulama:** mevcut repo testleri ve açık PR'lardan collision-free, küçük bir doğrulanmış onarım seçilir; plan sadece sıralama değildir, gerçek code+test kanıtı gerekir.