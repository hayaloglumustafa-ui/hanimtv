package tr.hayaloglu.hanimtv

/** Listedeki tek bir kanal. [no] sizin belirlediğiniz sıra numarasıdır ve asla kendiliğinden değişmez. */
data class Kanal(
    val no: Int,
    val isim: String,
    val linkler: List<String>,
    /** İnternet yayını sadece YouTube'da olan kanallar için resmi YouTube canlı yayın adresi. */
    val youtube: String? = null,
)
