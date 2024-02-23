package org.apache.hop.pipeline.transforms.aws.sqs;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;

import java.util.List;

public class AwsSqsReader {

    private AmazonSQSClient sqsClient;
    private SqsReaderMeta meta;
    private String awsKey;
    private String awsRegion;
    private String awsKeySecret;
    private BaseTransform baseTransform;
    private PipelineMeta transMeta;
    private String awsCredChain;
    private String deleteMessage;

    /**
     *
     * Constructor for new AWS SQS Object
     *
     * @param transformMeta	SqsReaderMeta
     * @param t		PipelineMeta
     * @param bst	BaseTransform
     */
    public AwsSqsReader(SqsReaderMeta transformMeta, PipelineMeta t, BaseTransform bst) {

        this.meta = transformMeta;
        this.baseTransform = bst;
        this.transMeta = t;

        this.awsCredChain = this.baseTransform.resolve(meta.getAwsCredChain());
        this.awsKey = this.baseTransform.resolve(meta.getAwsKey());
        this.awsKeySecret = this.baseTransform.resolve(meta.getAwsKeySecret());
        this.awsRegion = this.baseTransform.resolve(meta.getAwsRegion());
        this.deleteMessage = this.baseTransform.resolve(meta.getTFldMessageDelete());
    }

    /**
     *
     * Establishing new Connection to Amazon Webservices
     *
     * @return	true on successful connection
     */
    public boolean getAWSConnection() {
        try {
            baseTransform.logBasic("Starting connection to AWS SQS");

            if (this.awsCredChain.equalsIgnoreCase("N")) {
                BasicAWSCredentials awsCreds = new BasicAWSCredentials(this.awsKey, this.awsKeySecret);
                sqsClient = (AmazonSQSClient) AmazonSQSClientBuilder.standard()
                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                        .withRegion(this.awsRegion)
                        .build();

                baseTransform.logBasic("Connected to SQS in Region " + this.awsRegion + " with API-Key >>" + this.awsKey + "<<");

            } else {
                AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
                sqsClient = (AmazonSQSClient) AmazonSQSClientBuilder.standard()
                        .withCredentials(provider)
                        .build();

                baseTransform.logBasic("Connected to SQS with provided Credentials Chain");
            }
            return true;

        } catch (AmazonClientException e) {
            baseTransform.logError(e.getMessage());

        } catch (Exception e) {
            baseTransform.logError(e.getMessage());
        }
        return false;
    }

    /**
     * Disconnects from AWS
     */
    public void disconnectAWSConnection() {
        try {
            sqsClient.shutdown();

            baseTransform.logBasic("Disconnected from SQS");

        } catch (AmazonClientException e) {
            baseTransform.logError(e.getMessage());
            baseTransform.setErrors(1);
        }
    }

    /**
     *
     * @param queueURL
     * @param numMsgs
     * @param isPreview
     * @return
     * @throws AmazonSQSException
     */
    public List<Message> readMessages(String queueURL, int numMsgs, boolean isPreview) throws AmazonSQSException {

        int numMessages = (numMsgs > 10) ? 10 : numMsgs;

        try {

            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueURL);
            receiveMessageRequest.setMaxNumberOfMessages(numMessages);
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();

            baseTransform.logDebug(messages.size() + " Message(s) retrieved from queue");

            if (this.deleteMessage.equalsIgnoreCase("Y") && !isPreview) {

                for (Message m : messages) {
                    sqsClient.deleteMessage(queueURL, m.getReceiptHandle());
                }
                baseTransform.logDebug(messages.size() + " Message(s) deleted from queue");
            }

            return messages;

        } catch (AmazonSQSException e) {
            throw e;

        }
    }
}
