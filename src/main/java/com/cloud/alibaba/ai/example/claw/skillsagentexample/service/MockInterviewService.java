package com.cloud.alibaba.ai.example.claw.skillsagentexample.service;

import com.cloud.alibaba.ai.example.claw.skillsagentexample.service.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockInterviewService {

    private static final Logger logger = LoggerFactory.getLogger(MockInterviewService.class);


//    private final DashScopeChatModel chatModel;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    // 简单的内存存储（生产环境应该使用数据库或 Redis）
    private final Map<String, ResumeData> resumeStorage = new ConcurrentHashMap<>();

    @Value("classpath:/prompt/resume-analysis-system.st")
    Resource resumeAnalysisSystemPromptResource;
    @Value("classpath:/prompt/interview-evaluation-system.st")
    Resource interviewEvaluationSystemPromptresource;
    @Value("classpath:/prompt/interview-question-system.st")
    Resource interviewQuestionsSystemPromptresource;
    @Value("classpath:/prompt/resume-analysis-user.st")
    Resource resumeAnalysisUserresource;


    public MockInterviewService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 评分简历
     */
    public ResumeScoreResult scoreResume(String resumeText) throws IOException {
        logger.info("开始评分简历：{}", resumeText);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resumeAnalysisSystemPromptResource));


        // 构建用户提示
        PromptTemplate promptTemplate = new PromptTemplate(resumeAnalysisUserresource.getContentAsString(StandardCharsets.UTF_8));
        messages.add(new UserMessage( promptTemplate.render(Map.of("resumeText", resumeText))));
        
        Prompt prompt = new Prompt(messages, ChatOptions.builder()
                .temperature(0.7)
                .build());
        
        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("简历评分 AI 响应：{}", response);
        
        // 解析 JSON 响应
        return parseResumeScoreResult(response);
    }

    /**
     * 生成面试问题
     */
    public InterviewQuestions generateInterviewQuestions(String resumeText) throws JsonProcessingException {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(interviewQuestionsSystemPromptresource));
        
        String userPrompt = """
                请根据以下简历内容生成面试问题：
                
                ## 候选人简历
                %s
                """.formatted(resumeText);
        
        messages.add(new UserMessage(userPrompt));
        
        Prompt prompt = new Prompt(messages, ChatOptions.builder()
                .temperature(0.7)
                .build());
        
        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("面试问题 AI 响应：{}", response);
        
        return parseInterviewQuestions(response);
    }

    /**
     * 评估答案
     */
    public InterviewEvaluation evaluateAnswers(
            String resumeText,
            InterviewQuestions questions,
            Map<Integer, String> answers) throws JsonProcessingException {
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(interviewEvaluationSystemPromptresource));
        
        // 构建问题和答案的文本
        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questions.getQuestions().size(); i++) {
            InterviewQuestions.Question q = questions.getQuestions().get(i);
            String answer = StringUtils.isBlank(answers.get(i))? "未作答" :answers.get(i);
            qaText.append("问题 %d [%s]: %s\n".formatted(i + 1, q.getType(), q.getQuestion()));
            qaText.append("候选人回答：%s\n\n".formatted(answer));
        }
        
        String userPrompt = """
                请评估以下面试问答： 
                %s 
                """.formatted( qaText.toString());
        
        messages.add(new UserMessage(userPrompt));
        
        Prompt prompt = new Prompt(messages, ChatOptions.builder()
                .temperature(0.7)
                .build());
        
        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("答案评估 AI 响应：{}", response);

        return parseInterviewEvaluation(response);
    }

    /**
     * 保存简历数据
     */
    public void saveResume(String resumeId, String resumeText, ResumeScoreResult scoreResult) {
        ResumeData resumeData = ResumeData.builder()
                .resumeId(resumeId)
                .resumeText(resumeText)
                .scoreResult(scoreResult)
                .build();
        resumeStorage.put(resumeId, resumeData);
    }

    /**
     * 保存面试问题
     */
    public void saveQuestions(String resumeId, InterviewQuestions questions) {
        ResumeData resumeData = resumeStorage.get(resumeId);
        if (resumeData != null) {
            resumeData.setQuestions(questions);
            resumeStorage.put(resumeId, resumeData);
        }
    }

    /**
     * 保存评估结果
     */
    public void saveEvaluation(String resumeId, InterviewEvaluation evaluation) {
        ResumeData resumeData = resumeStorage.get(resumeId);
        if (resumeData != null) {
            resumeData.setEvaluation(evaluation);
            resumeStorage.put(resumeId, resumeData);
        }
    }

    /**
     * 获取简历数据
     */
    public ResumeData getResumeById(String resumeId) {
        return resumeStorage.get(resumeId);
    }

    private ResumeScoreResult parseResumeScoreResult(String json) throws JsonProcessingException {
        // 清理响应文本
        json = cleanJsonResponse(json);
        
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            
            Integer overallScore = rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0;
            String summary = rootNode.has("summary") ? rootNode.get("summary").asText() : "";
            
            ResumeScoreResult.ScoreDetail scoreDetail = new ResumeScoreResult.ScoreDetail();
            if (rootNode.has("scoreDetail")) {
                JsonNode detailNode = rootNode.get("scoreDetail");
                scoreDetail.setProjectScore(detailNode.has("projectScore") ? detailNode.get("projectScore").asInt() : 0);
                scoreDetail.setSkillMatchScore(detailNode.has("skillMatchScore") ? detailNode.get("skillMatchScore").asInt() : 0);
                scoreDetail.setContentScore(detailNode.has("contentScore") ? detailNode.get("contentScore").asInt() : 0);
                scoreDetail.setStructureScore(detailNode.has("structureScore") ? detailNode.get("structureScore").asInt() : 0);
                scoreDetail.setExpressionScore(detailNode.has("expressionScore") ? detailNode.get("expressionScore").asInt() : 0);
            }
            
            List<String> strengths = new ArrayList<>();
            if (rootNode.has("strengths") && rootNode.get("strengths").isArray()) {
                for (JsonNode item : rootNode.get("strengths")) {
                    strengths.add(item.asText());
                }
            }
            
            List<ResumeScoreResult.Suggestion> suggestions = new ArrayList<>();
            if (rootNode.has("suggestions") && rootNode.get("suggestions").isArray()) {
                for (JsonNode item : rootNode.get("suggestions")) {
                    ResumeScoreResult.Suggestion suggestion = new ResumeScoreResult.Suggestion();
                    suggestion.setCategory(item.has("category") ? item.get("category").asText() : "");
                    suggestion.setPriority(item.has("priority") ? item.get("priority").asText() : "");
                    suggestion.setIssue(item.has("issue") ? item.get("issue").asText() : "");
                    suggestion.setRecommendation(item.has("recommendation") ? item.get("recommendation").asText() : "");
                    suggestions.add(suggestion);
                }
            }
            
            return ResumeScoreResult.builder()
                    .overallScore(overallScore)
                    .scoreDetail(scoreDetail)
                    .summary(summary)
                    .strengths(strengths)
                    .suggestions(suggestions)
                    .build();
        } catch (Exception e) {
            logger.error("解析简历评分结果失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    private InterviewQuestions parseInterviewQuestions(String json) throws JsonProcessingException {
        json = cleanJsonResponse(json);
        
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            List<InterviewQuestions.Question> questions = new ArrayList<>();
            
            if (rootNode.has("questions") && rootNode.get("questions").isArray()) {
                for (JsonNode item : rootNode.get("questions")) {
                    InterviewQuestions.Question question = new InterviewQuestions.Question();
                    question.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    question.setType(item.has("type") ? item.get("type").asText() : "");
                    question.setCategory(item.has("category") ? item.get("category").asText() : "");
                    questions.add(question);
                }
            }
            
            return InterviewQuestions.builder()
                    .questions(questions)
                    .build();
        } catch (Exception e) {
            logger.error("解析面试问题失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    private InterviewEvaluation parseInterviewEvaluation(String json) throws JsonProcessingException {
        json = cleanJsonResponse(json);
        
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            
            InterviewEvaluation.InterviewEvaluationBuilder builder = InterviewEvaluation.builder();
            
            builder.sessionId(rootNode.has("sessionId") ? rootNode.get("sessionId").asText() : UUID.randomUUID().toString());
            builder.totalQuestions(rootNode.has("totalQuestions") ? rootNode.get("totalQuestions").asInt() : 0);
            builder.overallScore(rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0);
            builder.overallFeedback(rootNode.has("overallFeedback") ? rootNode.get("overallFeedback").asText() : "");
            
            List<InterviewEvaluation.CategoryScore> categoryScores = new ArrayList<>();
            if (rootNode.has("categoryScores") && rootNode.get("categoryScores").isArray()) {
                for (JsonNode item : rootNode.get("categoryScores")) {
                    InterviewEvaluation.CategoryScore score = new InterviewEvaluation.CategoryScore();
                    score.setCategory(item.has("category") ? item.get("category").asText() : "");
                    score.setScore(item.has("score") ? item.get("score").asInt() : 0);
                    score.setQuestionCount(item.has("questionCount") ? item.get("questionCount").asInt() : 0);
                    categoryScores.add(score);
                }
            }
            builder.categoryScores(categoryScores);
            
            List<InterviewEvaluation.QuestionDetail> questionDetails = new ArrayList<>();
            if (rootNode.has("questionDetails") && rootNode.get("questionDetails").isArray()) {
                for (JsonNode item : rootNode.get("questionDetails")) {
                    InterviewEvaluation.QuestionDetail detail = new InterviewEvaluation.QuestionDetail();
                    detail.setQuestionIndex(item.has("questionIndex") ? item.get("questionIndex").asInt() : 0);
                    detail.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    detail.setCategory(item.has("category") ? item.get("category").asText() : "");
                    detail.setUserAnswer(item.has("userAnswer") ? item.get("userAnswer").asText() : "");
                    detail.setScore(item.has("score") ? item.get("score").asInt() : 0);
                    detail.setFeedback(item.has("feedback") ? item.get("feedback").asText() : "");
                    questionDetails.add(detail);
                }
            }
            builder.questionDetails(questionDetails);
            
            List<String> strengths = new ArrayList<>();
            if (rootNode.has("strengths") && rootNode.get("strengths").isArray()) {
                for (JsonNode item : rootNode.get("strengths")) {
                    strengths.add(item.asText());
                }
            }
            builder.strengths(strengths);
            
            List<String> improvements = new ArrayList<>();
            if (rootNode.has("improvements") && rootNode.get("improvements").isArray()) {
                for (JsonNode item : rootNode.get("improvements")) {
                    improvements.add(item.asText());
                }
            }
            builder.improvements(improvements);
            
            List<InterviewEvaluation.ReferenceAnswer> referenceAnswers = new ArrayList<>();
            if (rootNode.has("referenceAnswers") && rootNode.get("referenceAnswers").isArray()) {
                for (JsonNode item : rootNode.get("referenceAnswers")) {
                    InterviewEvaluation.ReferenceAnswer answer = new InterviewEvaluation.ReferenceAnswer();
                    answer.setQuestionIndex(item.has("questionIndex") ? item.get("questionIndex").asInt() : 0);
                    answer.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    answer.setReferenceAnswer(item.has("referenceAnswer") ? item.get("referenceAnswer").asText() : "");
                    
                    List<String> keyPoints = new ArrayList<>();
                    if (item.has("keyPoints") && item.get("keyPoints").isArray()) {
                        for (JsonNode point : item.get("keyPoints")) {
                            keyPoints.add(point.asText());
                        }
                    }
                    answer.setKeyPoints(keyPoints);
                    referenceAnswers.add(answer);
                }
            }
            builder.referenceAnswers(referenceAnswers);
            
            return builder.build();
        } catch (Exception e) {
            logger.error("解析面试评估失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清理 JSON 响应，移除可能的 Markdown 标记
     */
    private String cleanJsonResponse(String json) {
        // 移除 markdown 代码块标记
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        
        return json.trim();
    }
}
