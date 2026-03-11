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

        //live 1 mediainfo 1 showinfo
        val OPH_MEDIA_V3_samp1 = """
            [
              {
                "n": "0:d:16",
                "t": 1710000000,
                "vs": "{\"records\":[{\"id\":0,\"show_id\":1001,\"show_info\":\"eyJtaWQiOjk4NzYzNCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MiwicmVwZWF0IjowLCJkYXlzIjo4MCwidHMiOls0MDY4MCw0MzIwMF0sImRzIjpbMTIwMCwxMDAwXSwiY3JlYXRlZCI6MTc3MjU5NzI4MCwic3RhcnQiOjE3NzMwMjUyNDUsImV4cGlyZWQiOjE3NzMwMzc4MDAsImZpbGVzIjpbeyJpZCI6NTAxLCJpbmRleCI6MCwidHlwZSI6MSwic2l6ZSI6MjQ1Njc4OSwiY3MiOjMwNTQxOTg5NiwiZHVyYXRpb24iOjY3LCJ1cmwiOiJodHRwczovL3Rob25ndGlubmd1b24udGhhaW5ndXllbi5nb3Yudm4vRG93bmxvYWQvN2Y5ODEzMzctYTliMC00ZDk0LWFhZTAtNjI2NTQ1MDdjZmZhLm1wMyJ9LHsiaWQiOjAsImluZGV4IjoxLCJ0eXBlIjowLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjIwLCJ1cmwiOiIifSx7ImlkIjo1MDIsImluZGV4IjoyLCJ0eXBlIjoyLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjMwLCJ1cmwiOiI5ODkwMCJ9LHsiaWQiOjUwMywiaW5kZXgiOjMsInR5cGUiOjMsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6MzAsInVybCI6InJ0c3A6Ly8xNzEuMjQ0LjEyOS4xNDM6MTkzNS9saXZldm92MSJ9XX0=\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"}]}"
              }
            ]
        """.trimIndent()

        //schedule tuần 1 mediainfo
        val OPH_MEDIA_V3_samp2 = """
            [
              {
                "n": "0:d:16",
                "t": 1710000000,
                    "vs": "{\"records\":[{\"id\":0,\"show_id\":1001,\"show_info\":\"eyJtaWQiOjk4NzY1NCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MSwicmVwZWF0IjowLCJkYXlzIjo5MywidHMiOls1Mzc0OSw2NDU0OV0sImRzIjpbMzAwLDMwMF0sImNyZWF0ZWQiOjE3NzI1OTcyODAsInN0YXJ0IjoxNzcyNjk3NDI0LCJleHBpcmVkIjoxNzcyNjk4NTA0LCJmaWxlcyI6W3siaWQiOjUwMSwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjI0NTY3ODksImNzIjozMDU0MTk4OTYsImR1cmF0aW9uIjozMCwidXJsIjoiaHR0cDovL2NvbnRlbnQuZXhhbXBsZS5jb20vYS81MDFfaGVsbG8ud2F2In0seyJpZCI6MCwiaW5kZXgiOjEsInR5cGUiOjAsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6NSwidXJsIjoiIn0seyJpZCI6NTAyLCJpbmRleCI6MiwidHlwZSI6Miwic2l6ZSI6MCwiY3MiOjAsImR1cmF0aW9uIjoyMCwidXJsIjoiOTg5MDAifSx7ImlkIjo1MDMsImluZGV4IjozLCJ0eXBlIjozLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjM1LCJ1cmwiOiJodHRwOi8vcmFkaW8uZXhhbXBsZS5jb20vdm92MS5tM3U4In1dfQ==\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"}]}"
              }
            ]
        """.trimIndent()

        //schedule tuần 1 mediainfo update lịch tuần
        val OPH_MEDIA_V3_samp2_2 = """
            [
              {
                "n": "0:d:16",
                "t": 1710000000,
                    "vs": "{\"records\":[{\"id\":0,\"show_id\":1001,\"show_info\":\"eyJtaWQiOjk4NzY1NCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MSwicmVwZWF0IjowLCJkYXlzIjo5MywidHMiOls1Mzc0OSw2NDU0OV0sImRzIjpbMzAwLDMwMF0sImNyZWF0ZWQiOjE3NzI1OTcyODAsInN0YXJ0IjoxNzcyNjk3NDI0LCJleHBpcmVkIjoxNzcyNjk4NTA0LCJmaWxlcyI6W3siaWQiOjUwMSwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjI0NTY3ODksImNzIjozMDU0MTk4OTYsImR1cmF0aW9uIjozMCwidXJsIjoiaHR0cDovL2NvbnRlbnQuZXhhbXBsZS5jb20vYS81MDFfaGVsbG8ud2F2In0seyJpZCI6MCwiaW5kZXgiOjEsInR5cGUiOjAsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6NSwidXJsIjoiIn0seyJpZCI6NTAyLCJpbmRleCI6MiwidHlwZSI6Miwic2l6ZSI6MCwiY3MiOjAsImR1cmF0aW9uIjoyMCwidXJsIjoiOTg5MDAifSx7ImlkIjo1MDMsImluZGV4IjozLCJ0eXBlIjozLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjM1LCJ1cmwiOiJodHRwOi8vcmFkaW8uZXhhbXBsZS5jb20vdm92MS5tM3U4In1dfQ==\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"}]}"
              }
            ]
        """.trimIndent()

        //schedule tuần 2 mediainfo
        val OPH_MEDIA_V3_samp3 = """
         [
          {
            "n": "0:d:16",
            "t": 1710000000,
            "vs": "{\"records\":[{\"id\":0,\"show_id\":1001,\"show_info\":\"eyJtaWQiOjk4NzY1NCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MSwicmVwZWF0IjowLCJkYXlzIjo5MywidHMiOls1Mzc0OSw2NDU0OV0sImRzIjpbMzAwLDMwMF0sImNyZWF0ZWQiOjE3NzI1OTcyODAsInN0YXJ0IjoxNzcyNjk3NDI0LCJleHBpcmVkIjoxNzcyNjk4NTA0LCJmaWxlcyI6W3siaWQiOjUwMSwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjI0NTY3ODksImNzIjozMDU0MTk4OTYsImR1cmF0aW9uIjozMCwidXJsIjoiaHR0cDovL2NvbnRlbnQuZXhhbXBsZS5jb20vYS81MDFfaGVsbG8ud2F2In0seyJpZCI6MCwiaW5kZXgiOjEsInR5cGUiOjAsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6NSwidXJsIjoiIn0seyJpZCI6NTAyLCJpbmRleCI6MiwidHlwZSI6Miwic2l6ZSI6MCwiY3MiOjAsImR1cmF0aW9uIjoyMCwidXJsIjoiOTg5MDAifSx7ImlkIjo1MDMsImluZGV4IjozLCJ0eXBlIjozLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjM1LCJ1cmwiOiJodHRwOi8vcmFkaW8uZXhhbXBsZS5jb20vdm92MS5tM3U4In1dfQ==\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"},{\"id\":1,\"show_id\":1002,\"show_info\":\"eyJtaWQiOjk4NzYzNCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MSwicmVwZWF0IjowLCJkYXlzIjo0MiwidHMiOls1Nzc4OCw2ODU4OF0sImRzIjpbMzAwLDMwMF0sImNyZWF0ZWQiOjE3NzI1OTcyODAsInN0YXJ0IjoxNzcyNjk3NDI0LCJleHBpcmVkIjoxNzcyNjk4NTA0LCJmaWxlcyI6W3siaWQiOjUwMSwiaW5kZXgiOjAsInR5cGUiOjEsInNpemUiOjI0NTY3ODksImNzIjozMDU0MTk4OTYsImR1cmF0aW9uIjozMCwidXJsIjoiaHR0cDovL2NvbnRlbnQuZXhhbXBsZS5jb20vYS81MDFfaGVsbG8ud2F2In0seyJpZCI6MCwiaW5kZXgiOjEsInR5cGUiOjAsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6NSwidXJsIjoiIn0seyJpZCI6NTAyLCJpbmRleCI6MiwidHlwZSI6Miwic2l6ZSI6MCwiY3MiOjAsImR1cmF0aW9uIjoyMCwidXJsIjoiOTg5MDAifSx7ImlkIjo1MDMsImluZGV4IjozLCJ0eXBlIjozLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjM1LCJ1cmwiOiJodHRwOi8vcmFkaW8uZXhhbXBsZS5jb20vdm92MS5tM3U4In1dfQ==\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"}]}"
          }
        ]
        """.trimIndent()

        //schedule Tháng 1 mediainfo
        val OPH_MEDIA_V3_samp4 = """
         [
          {
            "n": "0:d:16",
            "t": 1710000000,
            "vs": "{\"records\":[{\"id\":0,\"show_id\":1001,\"show_info\":\"eyJtaWQiOjk4NzYzNCwidmVyc2lvbiI6MywicHJpbyI6MTAsInN1bW1hcnkiOiJC4bqjbiB0aW4gdHXhuqduIFRo4butIG5naGnhu4dtIiwibW9kZSI6MSwicmVwZWF0IjoxLCJkYXlzIjoxNjQ5MjY4NDA1LCJ0cyI6WzU3Nzg4LDY4NTg4XSwiZHMiOlszMDAsMzAwXSwiY3JlYXRlZCI6MTc3MjU5NzI4MCwic3RhcnQiOjE3NzI2OTc0MjQsImV4cGlyZWQiOjE3NzI2OTg1MDQsImZpbGVzIjpbeyJpZCI6NTAxLCJpbmRleCI6MCwidHlwZSI6MSwic2l6ZSI6MjQ1Njc4OSwiY3MiOjMwNTQxOTg5NiwiZHVyYXRpb24iOjMwLCJ1cmwiOiJodHRwOi8vY29udGVudC5leGFtcGxlLmNvbS9hLzUwMV9oZWxsby53YXYifSx7ImlkIjowLCJpbmRleCI6MSwidHlwZSI6MCwic2l6ZSI6MCwiY3MiOjAsImR1cmF0aW9uIjo1LCJ1cmwiOiIifSx7ImlkIjo1MDIsImluZGV4IjoyLCJ0eXBlIjoyLCJzaXplIjowLCJjcyI6MCwiZHVyYXRpb24iOjIwLCJ1cmwiOiI5ODkwMCJ9LHsiaWQiOjUwMywiaW5kZXgiOjMsInR5cGUiOjMsInNpemUiOjAsImNzIjowLCJkdXJhdGlvbiI6MzUsInVybCI6Imh0dHA6Ly9yYWRpby5leGFtcGxlLmNvbS92b3YxLm0zdTgifV19\",\"action\":8,\"description\":\"Thong bao test\",\"version\":1,\"created_at\":\"2024-03-09T00:00:00Z\"}]}"
          }
        ]
        """.trimIndent()
        val live1 = """
         [{"n":"6565656565652:d:16","t":1773124800,"vs":"[{\"id\":283204,\"show_id\":283204,\"show_info\":\"eyJtaWQiOjI4MzIwNCwidmVyc2lvbiI6MSwicHJpbyI6OTk1LCJzdW1tYXJ5IjoiQuG6o24gdGluIGxpdmUiLCJtb2RlIjoxLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOlsgMzQ5MjJdLCJkcyI6WzE1MzhdLCJjcmVhdGVkIjoxNzczMTI0NzYzLCJzdGFydCI6MTc3MzE5NjY4MCwiZXhwaXJlZCI6MTc3MzIwNzQ4MCwiZmlsZXMiOlt7ImlkIjowLCJpbmRleCI6MCwidHlwZSI6Mywic2l6ZSI6MjAyOTM0LCJjcyI6MjAxMDI1ODgyMSwiZHVyYXRpb24iOjYsInVybCI6Imh0dHBzOi8vdHR0bS5tb2JpZm9uZS52bi91cGxvYWRGaWxlL2hlYWRlci9oZWFkZXIud2F2In0seyJpZCI6MjgzMjA0LCJpbmRleCI6MSwidHlwZSI6MSwic2l6ZSI6MTAzNDE2NTAsImNzIjoyODM3NTYyNTA3LCJkdXJhdGlvbiI6MzIzLCJ1cmwiOiJodHRwczovL3RydXllbnRoYW5odGhvbmdtaW5oLm1vYmlmb25lLmFpL3VwbG9hZEZpbGUvMjAyNjAzMTAvMjAyNjAzMTAxMjAxMDRfxJDGoW5fduG7i19i4bqndV9j4butX3Phu5FfMy5tcDMifV19\",\"action\":1,\"description\":\"283204\",\"version\":1,\"created_at\":\"2026-03-10T13:40:00.732696052+07:00\"}]"}] 
        """.trimIndent()
        val scheduler1 = """
            [
              {
                "n": "6565656565652:d:16",
                "t": 1773136583.5887241,
                "vs": "[{\"id\":283211,\"show_id\":283211,\"show_info\":\"eyJtaWQiOjI4MzIxMSwidmVyc2lvbiI6MSwicHJpbyI6OTk2LCJzdW1tYXJ5IjoiQuG6o24gdGluIGLhuqd1IGPhu60iLCJtb2RlIjoxLCJyZXBlYXQiOjAsImRheXMiOjEyNywidHMiOls1Nzk3OCw1OTEzOSw2MDMwMF0sImRzIjpbMTE2MCwxMTYwLDE3NDBdLCJjcmVhdGVkIjoxNzczMTMzNzIyLCJzdGFydCI6MTc3MzA3NTYwMCwiZXhwaXJlZCI6MTc3NDk3NjM5OSwiZmlsZXMiOlt7ImlkIjowLCJpbmRleCI6MCwidHlwZSI6Mywic2l6ZSI6MjAyOTM0LCJjcyI6MjAxMDI1ODgyMSwiZHVyYXRpb24iOjYsInVybCI6Imh0dHBzOi8vdHR0bS5tb2JpZm9uZS52bi91cGxvYWRGaWxlL2hlYWRlci9oZWFkZXIud2F2In0seyJpZCI6MjgzMjExLCJpbmRleCI6MSwidHlwZSI6MSwic2l6ZSI6MTgzOTIzODAsImNzIjo4NDYzNTY0NSwiZHVyYXRpb24iOjU3NCwidXJsIjoiaHR0cHM6Ly90cnV5ZW50aGFuaHRob25nbWluaC5tb2JpZm9uZS5haS91cGxvYWRGaWxlLzIwMjYwMzEwLzIwMjYwMzEwMTYwNDUyX8SQxqFuX3bhu4tfYuG6p3VfY+G7rV9z4buRXzQubXAzIn1dfQ==\",\"action\":1,\"description\":\"283211\",\"version\":1,\"created_at\":\"2026-03-10T16:13:16.171595227+07:00\"}]"
              }
            ]
        """.trimIndent()
    }
}