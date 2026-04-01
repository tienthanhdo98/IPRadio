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
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283204,\"show_id\":283204,\"show_info\":\"eyJtaWQiOjI4MzIwNCwidmVyc2lvbiI6MSwicHJpbyI6OTk1LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUiLCJtb2RlIjoyLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1NDQyMF0sImRzIjpbOTBdLCJjcmVhdGVkIjoxNzczMTI0NzYzLCJzdGFydCI6MTc3MzI0ODc2NiwiZXhwaXJlZCI6MTc3NTQzMDQ3NywiZmlsZXMiOlt7ImlkIjoyODMyMDQsImluZGV4IjoxLCJ0eXBlIjoyLCJzaXplIjoxMDM0MTY1MCwiY3MiOjI4Mzc1NjI1MDcsImR1cmF0aW9uIjozMCwidXJsIjoiOTEwMDAifSx7ImlkIjoyODMyMDQsImluZGV4IjoyLCJ0eXBlIjoxLCJzaXplIjoxMDM0MTY1MCwiY3MiOjI4Mzc1NjI1MDcsImR1cmF0aW9uIjo2MCwidXJsIjoiaHR0cHM6Ly9iaW5oZGluaC10dG4ubGNzYS52bi9zaXRlL2dldC1yZXNvdXJjZT9jb2RlPW5FNEpxM1I5T2pvUWVaZ002YXBaVmc4bE5ETTdQQSZ0eXBlPS5tcDMifV19\",\"action\":1,\"description\":\"283204\",\"version\":1,\"created_at\":\"2026-03-10T13:40:00.732696052+07:00\"}]"}] 
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