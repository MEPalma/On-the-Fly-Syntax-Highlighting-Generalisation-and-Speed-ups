package evaluator

abstract class HttpRequestObject

data class LoadModelRequest (
    val model_log_name: String,
    val model_index: Int
): HttpRequestObject()

data class LoadPygmentsRequest (
    val lang: String
): HttpRequestObject()

data class EvalWithModelRequest(
    val input_token_ids: List<Int>
): HttpRequestObject()

data class EvalWithModelResponse(
    val ns: Long,
    val ps: List<Int>
): HttpRequestObject()

data class EvalWithPygmentsRequest(
    val src: String
): HttpRequestObject()

data class EvalWithPygmentsResponse(
    val ns: Long,
    val res_json: String
): HttpRequestObject()

