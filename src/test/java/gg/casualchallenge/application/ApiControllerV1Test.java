package gg.casualchallenge.application;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

//@SpringBootTest
//@AutoConfigureMockMvc
//@ActiveProfiles("test")
//class ApiControllerV1Test {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Mock
//    private CasualChallengeService casualChallengeService;

//    @Test
//    void testGetCards_withValidNames() throws Exception {
//        List<CardVO> mockCards = List.of(new CardVO("Black Lotus"), new CardVO("Time Walk"));
//        Mockito.when(casualChallengeService.findCards(List.of("Black Lotus", "Time Walk"))).thenReturn(mockCards);
//        List<CardDTO> mockCardDTOs = CardMapper.INSTANCE.toDTOList(mockCards);
//
//        mockMvc.perform(get("/api/v1/cards")
//                        .param("names", "Black Lotus,Time Walk")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$", hasSize(2)))
//                .andExpect(jsonPath("$[0].name", is(mockCardDTOs.get(0).getName())))
//                .andExpect(jsonPath("$[1].name", is(mockCardDTOs.get(1).getName())));
//    }
//
//    @Test
//    void testGetCards_withEmptyNames() throws Exception {
//        Mockito.when(casualChallengeService.findCards(List.of())).thenReturn(List.of());
//
//        mockMvc.perform(get("/api/v1/cards")
//                        .param("names", "")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$", hasSize(0)));
//    }
//
//    @Test
//    void testGetCards_withNoParam() throws Exception {
//        Mockito.when(casualChallengeService.findCards(List.of())).thenReturn(List.of());
//
//        mockMvc.perform(get("/api/v1/cards")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$", hasSize(0)));
//    }
//}
