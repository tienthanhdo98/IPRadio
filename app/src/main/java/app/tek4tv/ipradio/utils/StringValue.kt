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
    }
}