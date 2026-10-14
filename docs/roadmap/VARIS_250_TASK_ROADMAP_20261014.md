# Varış Android V2 — 250 Görevlik Geliştirme Yol Haritası

> Oluşturulma: 2026-10-14 · Kaynak: `nazofobi-gpt/arrival-alarm-android-v2` main + `hafıza` H-003/G-xxx kanonik görevleri.
> Statü: PLAN; bu maddeler tek tek uygulanmış veya test edilmiş sayılmaz. Uygulama kararları için mevcut kanonik Görevler, aktüel main, açık PR'lar ve bağımlılık/kabul kapıları önceliklidir.
> Yürütme: kod işi yeni branch/PR; exact-head Android CI + API36 doğrulaması; ana dala birleştirmeden önce collision kontrolü; gerçek cihaz/saha kabulü ayrı. Kapalı/iptal V1 görevleri yeniden açılmaz.

| Alan | Görev sayısı |
|---|---:|
| Mimari ve ürün sözleşmeleri | 25 |
| Almanya GTFS veri alımı ve takvim | 25 |
| Realtime ve provider güvenilirliği | 25 |
| Arama, yakındaki durak ve istasyon keşfi | 25 |
| Rota motoru ve alternatif karşılaştırması | 25 |
| Canlı yolculuk rehberliği ve varış alarmı | 25 |
| Arayüz, temalar ve erişilebilirlik | 25 |
| Güvenlik, gizlilik ve ChatGPT connector | 25 |
| Performans, sağlamlık ve gözlemlenebilirlik | 25 |
| Test, yayın ve saha operasyonları | 25 |
| **Toplam** | **250** |

## Mimari ve ürün sözleşmeleri — P0 · kanonik G-149/G-173

- [ ] **VAR-001** · P0 · V2 üretim modüllerinin bağımlılık envanterini çıkar. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-002** · P0 · H-003 kanonik ürün sınırlarını belgeye bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-003** · P0 · Eski V1 görevi ile V2 kapsamını ayrıştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-004** · P0 · Sekiz ekranın navigation-state sözleşmesini doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-005** · P0 · UI ve domain state sınırlarını denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-006** · P0 · Journey state geçiş grafiğini çıkar. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-007** · P0 · Rotaya ait model kimliklerini tutarlı tanımla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-008** · P0 · TripInstance kalıcılık sözleşmesini denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-009** · P0 · Zaman kaynaklarının wall/monotonic ayrımını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-010** · P0 · Provider yetenek matrisini görünür hale getir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-011** · P0 · Eksik veride fail-closed davranışları envanterle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-012** · P0 · Kritik kullanıcı akışı için kabul matrisini kur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-013** · P0 · Gerçek cihaz gerektiren kapıları ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-014** · P0 · Çevrimdışı çalışmanın ürün sınırlarını tanımla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-015** · P0 · Anlık veri olmadan sunulacak iddiaları sınırla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-016** · P0 · Transit takvim gün değişimi sözleşmesini dondur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-017** · P0 · Kaynak sahipliği ve tazelik etiketlerini birleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-018** · P0 · Ana branch ile açık PR çakışma haritasını çıkar. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-019** · P0 · CI/API36 kalite kapısı eşiklerini belgeye bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-020** · P0 · Plan başlığını ölçülebilir kabul kanıtına bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-021** · P0 · Kapsam dışı özellikleri geri açmayı engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-022** · P0 · Mock verinin gerçek provider gibi görünmesini önle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-023** · P0 · PR başına tek davranış değişikliği kuralını uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-024** · P0 · Operasyonel geri alma ve recovery tasarla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-025** · P0 · G-136 gerçek saha kapısı için kanıt kontrol listesi oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Almanya GTFS veri alımı ve takvim — P0 · kanonik G-174/G-175/G-185

- [ ] **VAR-026** · P0 · Feed kimliği ve kaynak menşeini kalıcı tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-027** · P0 · GTFS ZIP bütünlük kontrolünü sertleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-028** · P0 · Zorunlu dosya yokluğunu açık hata olarak yakala. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-029** · P0 · Yanlış UTF-8 satırlarını raporla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-030** · P0 · CSV quoted-field ayrıştırmasını kenar değerlerle test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-031** · P0 · Aynı stop_id çakışmalarını kaynak bazında ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-032** · P0 · Parent station döngülerini reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-033** · P0 · Stop koordinat aralığını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-034** · P0 · Platform ve istasyon hiyerarşisini sınırla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-035** · P0 · Gidiş yönü/direction metnini doğru ilişkilendir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-036** · P0 · Stop_times sıralama bütünlüğünü doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-037** · P0 · GTFS 24 saat üzeri seferleri test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-038** · P0 · calendar ve calendar_dates üstünlüğünü test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-039** · P0 · DST ileri/geri geçişlerinde servis saatlerini test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-040** · P0 · Sadece exception kaynaklı servisleri doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-041** · P0 · Eksik route/agency referanslarını raporla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-042** · P0 · shape noktalarını yolculuk kimliğiyle eşleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-043** · P0 · Büyük feed indeks bellek bütçesini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-044** · P0 · Eski feed ile yeni feed arasında atomik takas yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-045** · P0 · Kesilmiş indirmeyi önceki offline veriyle izole et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-046** · P0 · ETag/304 yenilemeyi geri dönüş testiyle doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-047** · P0 · Feed geçerlilik tarih aralığını kullanıcıya açıkla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-048** · P0 · Disk alanı yokluğunda veri kaybını engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-049** · P0 · Feed silme/geri yükleme dayanıklılığını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-050** · P0 · Lohne gerçek feed stop örnekleriyle smoke testi kur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Realtime ve provider güvenilirliği — P0 · kanonik G-176/G-177/G-193

- [ ] **VAR-051** · P0 · Realtime snapshot tazelik süresini gözden geçir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-052** · P0 · Gelecek tarihli snapshotları fail-closed reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-053** · P0 · GTFS-RT TripUpdate kimliğini statik seferle eşleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-054** · P0 · Aynı seferin çift gecikme uygulanmasını engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-055** · P0 · İptal durumunu geçerli kalkışlardan ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-056** · P0 · RT verisi yokken statik fallback korunmasını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-057** · P0 · Provider timeout sonrasında circuit-state denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-058** · P0 · Exponential backoff üst sınırını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-059** · P0 · Tekrarlı HTTP 429 için retry-after uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-060** · P0 · Önbellek yaşını UI durumuna yansıt. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-061** · P0 · Bozuk protobuf girdisinde güvenli fallback göster. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-062** · P0 · Platform değişikliğini kaynakla ilişkilendir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-063** · P0 · Alert kapsamını stop/route/trip bazında uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-064** · P0 · Gecikme işaretini (+/-) tutarlı göster. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-065** · P0 · Sahte geriye giden tahminlere koruma ekle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-066** · P0 · Farklı provider stop_id alanlarını ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-067** · P0 · Çok bölgesel veri kaynaklarını tutarlı seç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-068** · P0 · Ağ iptalinde yeni provider isteği başlatma. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-069** · P0 · Circuit breaker recovery penceresini test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-070** · P0 · Sağlıklı sonraki çağrının kesinti sonrası çalışmasını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-071** · P0 · Yanıtlardaki gizli veriyi loga taşımayı engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-072** · P0 · Uç nokta HTTPS ve redirect politikasını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-073** · P0 · Provider health'ın yanlış READY olmasını engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-074** · P0 · Lohne→Achim gerçek RT kanıtları toplama akışını tanımla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-075** · P0 · RT başarısızlıklarını gizlilik korumalı teşhise bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Arama, yakındaki durak ve istasyon keşfi — P1 · kanonik G-168/G-170/G-171/G-182

- [ ] **VAR-076** · P1 · Almanca umlaut normalize edilmiş aramayı test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-077** · P1 · Türkçe karakterlerle eşdeğer arama desteğini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-078** · P1 · İstasyon takma adlarını indeksle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-079** · P1 · Arama metni boşken yanlış sonuç göstermeyi önle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-080** · P1 · Prefix ve token rank ayrımını ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-081** · P1 · Autocomplete gecikmesini büyük feed üzerinde ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-082** · P1 · Arama sonuçlarını provider kökeniyle etiketle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-083** · P1 · Aynı ad farklı şehir duraklarını ayrıştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-084** · P1 · Parent platform ile station sonuçlarını tekilleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-085** · P1 · İstasyon yön ve peron bilgisini koru. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-086** · P1 · Yakındaki durakları fiziksel mesafeyle sırala. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-087** · P1 · Bozuk koordinatlarla nearby hesabını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-088** · P1 · Yakındaki aramada GPS accuracy uyarısı sun. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-089** · P1 · Konum izni reddinde manuel arama sağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-090** · P1 · Eski last-known konumu current olarak göstermeyi önle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-091** · P1 · Sıfır sonuçta provider hata ayrımını göster. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-092** · P1 · Harita merkezinin koordinat sınırlarını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-093** · P1 · Antimeridian kamera hedef davranışını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-094** · P1 · Seçili origin/destination görünürlüğünü doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-095** · P1 · Kalkış panosunu rota yokken erişilebilir tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-096** · P1 · İptal edilen kalkışların seçilebilirliğini kaldır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-097** · P1 · Gün içi başka servis günü panosunu test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-098** · P1 · Çocuk platformlardan ana station panosuna çıkış yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-099** · P1 · Arama geçmişi silme işlevinin tutarlılığını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-100** · P1 · Lohne gerçek nearby and station aramasını sahada doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Rota motoru ve alternatif karşılaştırması — P1 · kanonik G-169/G-175/G-178/G-179

- [ ] **VAR-101** · P1 · Offline planner gerçek stop graph kullanıyor mu doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-102** · P1 · Origin ve destination aynı iken doğru sonuç ver. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-103** · P1 · Servis olmayan gün için doğru boş yanıt ver. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-104** · P1 · Geceden taşan yolculuk toplam süresini test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-105** · P1 · Transfer bekleme süresi negatifliğini reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-106** · P1 · Minimum değişim süresini rota kurallarına kat. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-107** · P1 · Aktarma kenarındaki yön dönüşünü kontrol et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-108** · P1 · Yürüme kenarı sürelerini gerçekçi sınırla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-109** · P1 · Rota döngülerini algoritmada engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-110** · P1 · Tekrar eden bağlantıların dedup sonucunu doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-111** · P1 · Farklı operator transit türlerini doğru birleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-112** · P1 · Seçilen kalkış saatinden önceki rotaları gizle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-113** · P1 · Sefer süresi taşmasını Long aritmetiğiyle yönet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-114** · P1 · Sadece direkt rota filtresini test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-115** · P1 · Filtrelemeden sonra kart limitini uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-116** · P1 · Alternatif rotalarda kalkış sıralamasını koru. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-117** · P1 · Birden fazla aynı skorlu rotada stabil sıra kullan. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-118** · P1 · Hızlı ama yüksek aktarmalı rotayı şeffaf karşılaştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-119** · P1 · En az yürüme tercihini açıkça tanımla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-120** · P1 · Erişilebilirlik verisi yoksa basamaksız iddia üretme. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-121** · P1 · Rota bekleme durumunu timeouttan ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-122** · P1 · Provider route yokken offline route fallback yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-123** · P1 · Provider yanlış READY iken rota isteğini engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-124** · P1 · Lohne→Achim normal ve offline performansını ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-125** · P1 · Gerçek cihazda rota sonuçlarını karşılaştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Canlı yolculuk rehberliği ve varış alarmı — P1 · kanonik G-180/G-181/G-136

- [ ] **VAR-126** · P1 · Seçilmiş trip kimliğini journey state'e bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-127** · P1 · Başlangıç ve varış durak rollerini kalıcılaştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-128** · P1 · Sonraki durak sırasını doğru ilerlet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-129** · P1 · Yolculuk planı değişirse kullanıcı onayı iste. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-130** · P1 · Yanlış location fix geldiğinde alarm tetikleme. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-131** · P1 · Zayıf yatay doğruluğu arrival kanıtından ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-132** · P1 · GPS kapatılınca retry durumunu göster. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-133** · P1 · İzin iptalinde foreground tracking'i durdur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-134** · P1 · Android service yeniden başlamasında journey kurtar. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-135** · P1 · Bozuk progress state'i fail-closed temizle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-136** · P1 · Aynı durak için çift varış alarmını önle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-137** · P1 · Durak geçildiğinde missed-stop recovery uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-138** · P1 · Arka plan geçişinde TTS bağlamını koru. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-139** · P1 · Bluetooth kopunca ses yönlendirmesini denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-140** · P1 · Doze altında kaybolan location event'leri test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-141** · P1 · Kilitte bildirim ve alarm görünürlüğünü doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-142** · P1 · TTS tamamlanmadan ekran durumunu yanlış güncelleme. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-143** · P1 · Monotonic zamanla kısa süreli alarm debounce uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-144** · P1 · Başka sefer seçildiğinde eski alarmları iptal et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-145** · P1 · Uzun yolculuk boyunca pil etkisini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-146** · P1 · Gerçek lokasyon simülasyonuyla sınır regresyonları kur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-147** · P1 · Sahada yanlış-pozitif varış oranını ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-148** · P1 · Sahada kaçırılan varış oranını ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-149** · P1 · Lohne gerçek cihaz Bluetooth/TTS matrisi oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-150** · P1 · G-136 fiziksel kabulünü sanal testten ayrı tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Arayüz, temalar ve erişilebilirlik — P1 · kanonik G-160/G-167/G-166/G-184

- [ ] **VAR-151** · P1 · Onboarding light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-152** · P1 · Home light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-153** · P1 · Search light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-154** · P1 · Route results light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-155** · P1 · Journey detail light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-156** · P1 · Live trip light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-157** · P1 · Station departures light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-158** · P1 · Settings light/dark gerçek render karşılaştırması yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-159** · P1 · Matte tema contrast AA sınırlarını denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-160** · P1 · 200% font boyutu ile tüm ekranları test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-161** · P1 · TalkBack odak sıralamasını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-162** · P1 · TalkBack için durak anons etiketi ekle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-163** · P1 · Buton dokunma hedeflerini erişilebilir ölçüde tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-164** · P1 · Sadece renkle iletilen durumları düzelt. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-165** · P1 · Hata açıklamalarında eylem odaklı metin kullan. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-166** · P1 · Boş arama ve boş rota ekranlarını ayrıştır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-167** · P1 · Yükleme placeholder'larında titremeyi azalt. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-168** · P1 · Ekran rotasyonunda seçim state'ini koru. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-169** · P1 · Küçük ekran kırpılmasını engelle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-170** · P1 · Tablet ekranında iki panel ölçeklemesini denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-171** · P1 · Klavye açıkken arama sonuçlarını kullanılabilir tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-172** · P1 · Harita koordinat seçimini klavyeyle erişilebilir yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-173** · P1 · Geri tuşu davranışını ekran durumuna bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-174** · P1 · İzin ekranında gerekçe ve alternatif sun. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-175** · P1 · Gerçek cihazdan kanıta dayalı 8 ekran kabul seti çıkar. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Güvenlik, gizlilik ve ChatGPT connector — P1 · kanonik G-153/G-191/G-193

- [ ] **VAR-176** · P1 · Connector komut tiplerinin strict JSON şemasını denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-177** · P1 · Idempotency claim bozuk verisini fail-closed reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-178** · P1 · Persisted connector journal bozuksa komut çalıştırma. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-179** · P1 · Komut koordinatlarını sonlu ve aralık içi doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-180** · P1 · Kullanıcı rızası olmadan journey mutation yapma. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-181** · P1 · OAuth callback state/nonce doğrulamasını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-182** · P1 · OAuth credential-bearing URL'leri reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-183** · P1 · Token içeren hata metinlerini redakte et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-184** · P1 · HTTP diagnostic URL parametrelerini temizle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-185** · P1 · Uygulama gizli anahtarlarının paketlenmesini önle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-186** · P1 · Connector hesap silmede tokenları temizle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-187** · P1 · Dışa verilen journey state'te hassas konumu sınırla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-188** · P1 · Açık izin modeline uygun read-only komutlar ayır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-189** · P1 · Komut replay saldırısına süre sınırı uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-190** · P1 · Connector sunucu yeniden başlatma idempotency testi yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-191** · P1 · Silinmiş journey üzerinde gecikmiş komutları reddet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-192** · P1 · Manifest foreground location iznini CI'da doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-193** · P1 · Android 16 FGS servis türünü denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-194** · P1 · Runtime permission revocation test matrisi kur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-195** · P1 · Diagnostik paylaşımında konum redaksiyonunu doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-196** · P1 · Hata günlüklerinde e-posta ve token sızıntısını test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-197** · P1 · Play Data Safety veri akışı tablosu oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-198** · P1 · Gizlilik metnindeki saklama ve silme sözünü doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-199** · P1 · Kullanıcı verisi temizleme işlevini uçtan uca test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-200** · P1 · G-153 gateway gerçek cihaz kabulünü bağımsız tut. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Performans, sağlamlık ve gözlemlenebilirlik — P2 · kanonik G-190/G-173/G-193

- [ ] **VAR-201** · P2 · Germany Full ilk açılış süresini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-202** · P2 · Nationwide index ilk arama gecikmesini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-203** · P2 · Rota hesaplama p50/p95 gecikmesini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-204** · P2 · GTFS yükleme tepe RAM tüketimini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-205** · P2 · Başlangıç frame jank değerlerini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-206** · P2 · Yakındaki arama tekrarlı hesaplamalarını azalt. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-207** · P2 · Stale veri cache hit yüzdesini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-208** · P2 · Network transfer üst sınırını belirle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-209** · P2 · Provider retry sayısını diagnostikte say. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-210** · P2 · Circuit breaker kararlarını metrikleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-211** · P2 · Crash-free session metriğini anonim hale getir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-212** · P2 · Arka plan battery drain ölçüm taslağı oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-213** · P2 · Cold-start ve warm-start kıyas testi ekle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-214** · P2 · Boş batarya/enerji kısıtında degradeyi test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-215** · P2 · Disk dolu offline feed güncellemesini test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-216** · P2 · OOM durumunda önceki offline feed'i koru. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-217** · P2 · İş parçacığı iptalinde network I/O başlatmama testi ekle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-218** · P2 · Retry fırtınasında rate-limit korumasını denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-219** · P2 · Büyük sonuç listesinde Compose recomposition ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-220** · P2 · Harita kamera güncelleme maliyetini ölç. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-221** · P2 · ANR riski olan main-thread dosya işlerini tespit et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-222** · P2 · Yanlış readiness ölçümü için regresyon testi ekle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-223** · P2 · Performans regresyon eşikleri için CI raporu oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-224** · P2 · Saha cihaz metriklerini kişisel veri içermeden kaydet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-225** · P2 · İyileşmeleri önce/sonra ölçümle belgeye bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

## Test, yayın ve saha operasyonları — P2 · kanonik G-119/G-120/G-136/G-138

- [ ] **VAR-226** · P2 · Unit testleri doğrulanmış ana commit'e bağla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-227** · P2 · Android CI ve API36 dual-green kontrolü uygula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-228** · P2 · Açık PR çakışma/üst üste binme denetimi yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-229** · P2 · Test APK'nın exact commit kimliğini yazdır. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-230** · P2 · Temiz kurulum smoke testini otomatikleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-231** · P2 · Uygulama yükseltme senaryosunu test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-232** · P2 · Offline ilk açılış senaryosunu test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-233** · P2 · İzin reddi ve geri verme senaryosunu test et. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-234** · P2 · Bluetooth route değiştirme testini kaydet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-235** · P2 · GPS provider kapat-aç testini kaydet. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-236** · P2 · Kilit ekranı bildirimini fiziksel cihazda doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-237** · P2 · Android 16 izin ve service davranışını doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-238** · P2 · TR çeviri yer tutucularını CI ile doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-239** · P2 · DE çeviri yer tutucularını CI ile doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-240** · P2 · EN çeviri yer tutucularını CI ile doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-241** · P2 · Yaz saati geçişi takvim testlerini otomatikleştir. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-242** · P2 · Sahadan redacted failure replay aracı tasarla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-243** · P2 · Beta APK checksum ve provenance oluştur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-244** · P2 · Release notlarını doğrulanmış özelliklerle sınırla. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-245** · P2 · Play preflight mağaza görsel ve metnini denetle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-246** · P2 · Data Safety taahhütlerini somut veri akışıyla doğrula. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-247** · P2 · Lohne varış ve çıkış senaryosu uygulama testini yap. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-248** · P2 · Bremen/VBN sahadaki gecikme ve platform değişimini gözle. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-249** · P2 · Field acceptance dışındaki kapılara DONE verme. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.
- [ ] **VAR-250** · P2 · G-136 PASS sonrası final Play RC sürümünü dondur. **Kabul:** İlgili G-kapsamı içinde kod/kanıt, tam regresyon ve gerçek kaynak doğrulaması; fiziksel test gerekliyse ayrı saha makbuzu.

