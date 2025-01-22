package gg.casualchallenge.application;

import gg.casualchallenge.application.api.CasualChallengeService;
import gg.casualchallenge.application.mapper.CardMapper;
import gg.casualchallenge.application.model.response.CardDTO;
import gg.casualchallenge.application.model.values.CardVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
