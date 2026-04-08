package app.tek4tv.ipradio.utils

class StringValue {
    companion object {

        //Device info
        const val deviceInfoPA = "PA"
        const val deviceInfoVolume = "Volume"
        const val deviceInfoTemperature = "Temperature"

        //PA
        const val PAStatusOn = "1"
        const val PAStatusOff = "0"

        //
        const val UART_S1_NAME = "/dev/ttyS1"
        const val UART_S4_NAME = "/dev/ttyS4"

        //
        val OPH_SETTINGS = """
            [
              {
                "n": "0:d:2",
                "t": 1710000000,
                "vs": "{\"txtype\":1,\"sim\":0,\"mobile_mode\":4,\"ioct\":0,\"classd\":1,\"disabled\":0,\"main_volume\":70,\"fm_volume\":80,\"fm_auto\":1,\"fm_threshold\":15}"
              }
            ]
        """.trimIndent()
        val OPH_MEDIA_DELETE_ALL = """
             [
              {
                "n": "0:d:17",
                "t": 1710000000,
                "vs": "{}"
              }
            ]
        """.trimIndent()

        //0: reset cứng 1: reset app
        val OPH_REBOOT = """
            [
              {
                "n": "0:d:5",
                "t": 1710000000,
                "vs": "{\"option\": 0}"
              }
            ]
        """.trimIndent()


        //Live cơ bản. nhạc hiệu gốc
        val live1 = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283204,\"show_id\":283204,\"show_info\":\"eyJtaWQiOjI4MzIwNCwidmVyc2lvbiI6MSwicHJpbyI6OTk1LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1NDQyMF0sImRzIjpbMTIwXSwiY3JlYXRlZCI6MTc3MzEyNDc2Mywic3RhcnQiOjE3NzMyNDg3NjYsImV4cGlyZWQiOjE3NzQ5OTg0NzcsImZpbGVzIjpbeyJpZCI6MCwiaW5kZXgiOjAsInR5cGUiOjMsInNpemUiOjIwMjkzNCwiY3MiOjIwMTAyNTg4MjEsImR1cmF0aW9uIjo2LCJ1cmwiOiJodHRwczovL3R0dG0ubW9iaWZvbmUudm4vdXBsb2FkRmlsZS9oZWFkZXIvaGVhZGVyLndhdiJ9LHsiaWQiOjI4MzIwNCwiaW5kZXgiOjEsInR5cGUiOjEsInNpemUiOjEwMzQxNjUwLCJjcyI6MjgzNzU2MjUwNywiZHVyYXRpb24iOjEyMCwidXJsIjoiaHR0cDovL3NoLnRydXllbnRoYW5odGhvbmdtaW5oLnZuL01CRl81MzIifV19\",\"action\":1,\"description\":\"283204\",\"version\":1,\"created_at\":\"2026-03-10T13:40:00.732696052+07:00\"}]"}] 
        """.trimIndent()
        val liveFm = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283204,\"show_id\":283204,\"show_info\":\"eyJtaWQiOjI4MzIwNCwidmVyc2lvbiI6MSwicHJpbyI6OTk1LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1NDQyMF0sImRzIjpbMTIwXSwiY3JlYXRlZCI6MTc3MzEyNDc2Mywic3RhcnQiOjE3NzMyNDg3NjYsImV4cGlyZWQiOjE3NzU0MzA0NzcsImZpbGVzIjpbeyJpZCI6MjgzMjA0LCJpbmRleCI6MSwidHlwZSI6Miwic2l6ZSI6MTAzNDE2NTAsImNzIjoyODM3NTYyNTA3LCJkdXJhdGlvbiI6NjAsInVybCI6IjkxMDAwIn0seyJpZCI6MjgzMjA0LCJpbmRleCI6MiwidHlwZSI6MSwic2l6ZSI6MTAzNDE2NTAsImNzIjoyODM3NTYyNTA3LCJkdXJhdGlvbiI6NjAsInVybCI6Imh0dHBzOi8vYmluaGRpbmgtdHRuLmxjc2Eudm4vc2l0ZS9nZXQtcmVzb3VyY2U/Y29kZT1uRTRKcTNSOU9qb1FlWmdNNmFwWlZnOGxORE03UEEmdHlwZT0ubXAzIn1dfQ==\",\"action\":1,\"description\":\"283204\",\"version\":1,\"created_at\":\"2026-03-10T13:40:00.732696052+07:00\"}]"}] 
        """.trimIndent()

        val liveFmSohoaLoi = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283204,\"show_id\":283204,\"show_info\":\"eyJtaWQiOjI4MzIwNCwidmVyc2lvbiI6MSwicHJpbyI6OTk1LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1NDQyMF0sImRzIjpbMzAwXSwiY3JlYXRlZCI6MTc3MzEyNDc2Mywic3RhcnQiOjE3NzMyNDg3NjYsImV4cGlyZWQiOjE3NzU0MzA0NzcsImZpbGVzIjpbeyJpZCI6MjgzMjA0LCJpbmRleCI6MSwidHlwZSI6Miwic2l6ZSI6MTAzNDE2NTAsImNzIjoyODM3NTYyNTA3LCJkdXJhdGlvbiI6MTIwLCJ1cmwiOiI5MDAwMCJ9LHsiaWQiOjI4MzIwNCwiaW5kZXgiOjIsInR5cGUiOjMsInNpemUiOjEwMzQxNjUwLCJjcyI6MjgzNzU2MjUzMiwiZHVyYXRpb24iOjMwLCJ1cmwiOiJodHRwOi8vc2gudHJ1eWVudGhhbmh0aG9uZ21pbmgudm4vTUJGXzUzMiJ9LHsiaWQiOjI4MzIxMiwiaW5kZXgiOjIsInR5cGUiOjMsInNpemUiOjEwMzQxNjUwLCJjcyI6MjgzNzU2MjUwNywiZHVyYXRpb24iOjkwLCJ1cmwiOiJodHRwczovL3N0ci52b3YuZ292LnZuL3ZvdmxpdmUvdm92MXZvdjVWaWV0bmFtZXNlLnNkcF9hYWMvcGxheWxpc3QubTN1OCJ9LHsiaWQiOjI4NTIxMiwiaW5kZXgiOjIsInR5cGUiOjMsInNpemUiOjEwMzQxNjUwLCJjcyI6MjgzNzU2MjUwNywiZHVyYXRpb24iOjYwLCJ1cmwiOiJodHRwOi8vMTE1Ljc5LjQ2LjE2NDoxOTM1L3BoYXR0aGFuaC9jaG9nYW9kdC9wbGF5bGlzdC5tM3U4In1dfQ==\",\"action\":1,\"description\":\"283204\",\"version\":1,\"created_at\":\"2026-03-10T13:40:00.732696052+07:00\"}]"}] 
        """.trimIndent()

        val live = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283625,\"show_id\":283625,\"show_info\":\"eyJtaWQiOjI4MzYyNSwidmVyc2lvbiI6MSwicHJpbyI6OTk5LCJzdW1tYXJ5IjoicGjDoXQgYuG6o24gdGluIGtjIiwibW9kZSI6MiwicmVwZWF0IjowLCJkYXlzIjoxMjcsInRzIjpbNTg2MzNdLCJkcyI6WzU1Ml0sImNyZWF0ZWQiOjE3NzU0NjcwNjcsInN0YXJ0IjoxNzc1NDY3MDkzLCJleHBpcmVkIjoxNzc1NTE4NDEyLCJmaWxlcyI6W3siaWQiOjAsImluZGV4IjowLCJ0eXBlIjoxLCJzaXplIjo0MjM0MjI4LCJjcyI6MjY3OTI5NzUwMywiZHVyYXRpb24iOjI1OSwidXJsIjoiaHR0cHM6Ly90cnV5ZW50aGFuaHRob25nbWluaC5tb2JpZm9uZS5haS91cGxvYWRGaWxlLzIwMjYwNDA2LzIwMjYwNDA2MTYxNzM0X0RlTWlOb2lDaG9NYU5naGVDYW9SZW1peC1Ib2FuZ1RodXlMaW5oLTYwMTEyNDYubXAzIn0seyJpZCI6MjgzNjI1LCJpbmRleCI6MSwidHlwZSI6MSwic2l6ZSI6NDc3NDE2NiwiY3MiOjQyNTMxMzkzNzUsImR1cmF0aW9uIjoyOTMsInVybCI6Imh0dHBzOi8vdHJ1eWVudGhhbmh0aG9uZ21pbmgubW9iaWZvbmUuYWkvdXBsb2FkRmlsZS8yMDI2MDQwNi8yMDI2MDQwNjE2MTcyMl9UaGFuaFh1YW5WdW9uZ1RyZW5WYWlHYXktUXV5ZXRUaWVuTWF6R2lhQmFvLTY5MjY5MjYubXAzIn1dfQ==\",\"action\":1,\"description\":\"283625\",\"version\":1,\"created_at\":\"2026-04-06T16:17:51.740385026+07:00\"}]"}] 
        """.trimIndent()

        val liveHttp = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283625,\"show_id\":283625,\"show_info\":\"eyJtaWQiOjI4MzYyNSwidmVyc2lvbiI6MSwicHJpbyI6OTk5LCJzdW1tYXJ5IjoicGjDoXQgYuG6o24gdGluIGtjIiwibW9kZSI6MiwicmVwZWF0IjowLCJkYXlzIjoxMjcsInRzIjpbNTg2MzNdLCJkcyI6WzYwXSwiY3JlYXRlZCI6MTc3NTQ2NzA2Nywic3RhcnQiOjE3NzU0NjcwOTMsImV4cGlyZWQiOjE3NzU2OTIzNDEsImZpbGVzIjpbeyJpZCI6MjgzNjI1LCJpbmRleCI6MCwidHlwZSI6Mywic2l6ZSI6NDc3NDE2NiwiY3MiOjQyNTMxMzkzNzUsImR1cmF0aW9uIjo2MCwidXJsIjoiaHR0cDovL3NoLnRydXllbnRoYW5odGhvbmdtaW5oLnZuL3BoYXR0aGFuaCJ9XX0=\",\"action\":1,\"description\":\"283625\",\"version\":1,\"created_at\":\"2026-04-06T16:17:51.740385026+07:00\"}]"}] 
        """.trimIndent()


        val liveCount = """
         [{"n":"656565656565:d:16","t":1775547559.5141876,"vs":"[{\"id\":283645,\"show_id\":283645,\"show_info\":\"eyJtaWQiOjI4MzY0NSwidmVyc2lvbiI6MSwicHJpbyI6OTk5LCJzdW1tYXJ5IjoiVGVrdGVzdF9rY18xLjEiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1MjcyM10sImRzIjpbNTZdLCJjcmVhdGVkIjoxNzc1NTQ3NTU2LCJzdGFydCI6MTc3NTU0NzU4MSwiZXhwaXJlZCI6MTc3NTY5MjM0MSwiZmlsZXMiOlt7ImlkIjowLCJpbmRleCI6MCwidHlwZSI6Mywic2l6ZSI6MjAyOTM0LCJjcyI6MjAxMDI1ODgyMSwiZHVyYXRpb24iOjYsInVybCI6Imh0dHBzOi8vdHR0bS5tb2JpZm9uZS52bi91cGxvYWRGaWxlL2hlYWRlci9oZWFkZXIud2F2In0seyJpZCI6MjgzNjQ1LCJpbmRleCI6MSwidHlwZSI6MSwic2l6ZSI6MjAxOTE3LCJjcyI6MTYzODQ5MDY2OSwiZHVyYXRpb24iOjgsInVybCI6Imh0dHBzOi8vdHJ1eWVudGhhbmh0aG9uZ21pbmgubW9iaWZvbmUuYWkvdXBsb2FkRmlsZS8yMDI2MDQwNy8yMDI2MDQwNzEzNTcxMV9hdWRpb19yZWNvcmRfMjAyNjA0MDdfMTM1NzM4LndlYm0ubXAzIn1dfQ==\",\"action\":1,\"description\":\"283645\",\"version\":1,\"created_at\":\"2026-04-07T14:39:19.513765392+07:00\"}]"}] 
        """.trimIndent()

        //ko nhac hieu, loop 2
        val Live2 = """
            [{"n":"6565656565652:d:16","t":1773723365.776149,"vs":"[{\"id\":283331,\"show_id\":283331,\"show_info\":\"eyJtaWQiOjI4MzMzMSwidmVyc2lvbiI6MSwicHJpbyI6OTk5LCJzdW1tYXJ5IjoiTmjhuqFjIHRy4bq7IDMiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjAsInRzIjpbNDMwMjVdLCJkcyI6WzM3Ml0sImNyZWF0ZWQiOjE3NzM3MjMzNjIsInN0YXJ0IjoxNzczNzIzNDcxLCJleHBpcmVkIjoxNzczNzI3NDQzLCJmaWxlcyI6W3siaWQiOjI4MzMzMSwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjI5ODAxODYsImNzIjoyODYxODUyNjEyLCJkdXJhdGlvbiI6MTg2LCJ1cmwiOiJodHRwczovL3RydXllbnRoYW5odGhvbmdtaW5oLm1vYmlmb25lLmFpL3VwbG9hZEZpbGUvMjAyNjAzMTMvMjAyNjAzMTMxNjQ4MzRfdHQubXAzIn1dfQ==\",\"action\":1,\"description\":\"283331\",\"version\":1,\"created_at\":\"2026-03-17T11:56:05.775801452+07:00\"}]"}]
        """.trimIndent()

        //Nhac hieu la file upload, loop2,
        val Live3 = """
            [{"n":"6565656565652:d:16","t":1773732893.604526,"vs":"[{\"id\":283338,\"show_id\":283338,\"show_info\":\"eyJtaWQiOjI4MzMzOCwidmVyc2lvbiI6MSwicHJpbyI6OTk5LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUgMTcvMyIsIm1vZGUiOjIsInJlcGVhdCI6MCwiZGF5cyI6MCwidHMiOls1MjU1M10sImRzIjpbMjUzXSwiY3JlYXRlZCI6MTc3MzczMjg4OSwic3RhcnQiOjE3NzM3MzI5OTcsImV4cGlyZWQiOjE3NzM3MzY4NTAsImZpbGVzIjpbeyJpZCI6MCwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjU0MjkyOCwiY3MiOjM1MjY2MTAzMjYsImR1cmF0aW9uIjo2NywidXJsIjoiaHR0cHM6Ly90cnV5ZW50aGFuaHRob25nbWluaC5tb2JpZm9uZS5haS91cGxvYWRGaWxlLzIwMjYwMzE3LzIwMjYwMzE3MTQzNDQ0X2Nvbmd0YWNjaHVhbmJpLm1wMyJ9LHsiaWQiOjI4MzMzOCwiaW5kZXgiOjEsInR5cGUiOjEsInNpemUiOjI5ODAxODYsImNzIjoyODYxODUyNjEyLCJkdXJhdGlvbiI6MTg2LCJ1cmwiOiJodHRwczovL3RydXllbnRoYW5odGhvbmdtaW5oLm1vYmlmb25lLmFpL3VwbG9hZEZpbGUvMjAyNjAzMTcvMjAyNjAzMTcxNDMzNTBfdHQubXAzIn1dfQ==\",\"action\":1,\"description\":\"283338\",\"version\":1,\"created_at\":\"2026-03-17T14:34:53.604108587+07:00\"}]"}]
        """.trimIndent()

    }
}