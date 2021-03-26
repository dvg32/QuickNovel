package com.example.epubdownloader

import org.jsoup.Jsoup
import java.lang.Exception

class NovelPassionProvider : MainAPI() {

    override val name: String get() = "Novel Passion"
    override val mainUrl: String get() = "https://www.novelpassion.com"

    override fun loadPage(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            val res = document.selectFirst("div.cha-words")
            if(res.html() == "") {
                return null
            }
            res.html()
        } catch (e: Exception) {
            null
        }
    }

    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response = khttp.get("$mainUrl/search?keyword=$query")

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.lh1d5")
            if (headers.size <= 0) return null
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val head = h.selectFirst("a.c_000");
                val name = head.attr("title")
                val url = mainUrl + head.attr("href")

                var posterUrl = head.selectFirst("i.oh > img").attr("src")
                if (posterUrl != null) posterUrl = mainUrl + posterUrl

                val rating = (h.selectFirst("p.g_star_num > small").text()!!.toFloat() * 20).toInt()
                val latestChapter = h.selectFirst("div > div.dab > a").attr("title")
                returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }

    override fun load(url: String): LoadResponse? {
        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)
            val name = document.selectFirst("h2.pt4").text()!!
            val author = document.selectFirst("a.stq").text()!!
            val posterUrl = mainUrl + document.select("i.g_thumb > img").attr("src")
            val tags: ArrayList<String> = ArrayList()

            val rating = (document.select("strong.vam").text().toFloat() * 20).toInt()
            var synopsis = ""
            val synoParts = document.select("div.g_txt_over > div.c_000 > p")
            for (s in synoParts) {
                synopsis += s.text()!! + "\n"
            }

            val infoheader =
                document.select("div.psn > address.lh20 > div.dns") // 0 = Author, 1 = Tags

            val tagsHeader = infoheader[1].select("a.stq")
            for (t in tagsHeader) {
                tags.add(t.text())
            }

            val data: ArrayList<ChapterData> = ArrayList()
            val chapterHeaders = document.select("ol.content-list > li > a")
            for (c in chapterHeaders) {
                val url = mainUrl + c.attr("href")
                val name = c.select("span.sp1").text()
                val added = c.select("i.sp2").text()
                val views = c.select("i.sp3").text().toInt()
                data.add(ChapterData(name, url, added, views))
            }
            data.reverse()

            val peopleVotedText = document.selectFirst("small.fs16").text()!!
            val peopleVoted = peopleVotedText.substring(1, peopleVotedText.length - 9).toInt()
            val views = document.selectFirst("address > p > span").text().replace(",", "").toInt()

            return LoadResponse(name, data, author, posterUrl, rating, peopleVoted, views, synopsis, tags)
        } catch (e: Exception) {
            return null
        }
    }
}