package dev.pasbox.apidevices.androidregister.dynamodb

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.security.cert.Certificate
import java.time.Instant

inline fun <T> T?.dynamoNullOr(fn: T.(builder: AttributeValue.Builder) -> AttributeValue.Builder): AttributeValue =
  if (null == this) {
    AttributeValue.builder().nul(true).build()
  } else {
    fn(this, AttributeValue.builder()).build()
  }

inline fun Int?.toDynamo() = this.dynamoNullOr { it.n(this.toString()) }
inline fun Long?.toDynamo() = this.dynamoNullOr { it.n(this.toString()) }
inline fun String?.toDynamo() = this.dynamoNullOr { it.s(this) }
inline fun ByteArray?.toDynamo() = this.dynamoNullOr { it.b(SdkBytes.fromByteArray(this)) }
inline fun Instant?.toDynamo() = this.dynamoNullOr { it.n(this.toEpochMilli().toString()) }
inline fun Map<String, AttributeValue>?.toDynamo() = this.dynamoNullOr { it.m(this) }

inline fun Certificate?.toDynamo() = this.dynamoNullOr { it.b(SdkBytes.fromByteArray(this.encoded)) }
inline fun List<Certificate?>?.toDynamo() = this.dynamoNullOr { it.l(this.map { dynamoNullOr { it } }) }

inline infix fun String.dynamo(value: Int?) = Pair(this, value.toDynamo())
inline infix fun String.dynamo(value: String?) = Pair(this, value.toDynamo())
inline infix fun String.dynamo(value: ByteArray?) = Pair(this, value.toDynamo())
inline infix fun String.dynamo(value: Instant?) = Pair(this, value.toDynamo())
inline infix fun String.dynamo(value: Map<String, AttributeValue>?) = Pair(this, value.toDynamo())

inline infix fun String.dynamo(value: Certificate?) = Pair(this, value.toDynamo())
inline infix fun String.dynamo(value: List<Certificate?>?) = Pair(this, value.toDynamo())

class UpdateItemDSL(val table: String) {
  internal val key = hashMapOf<String, AttributeValue>()
  internal val expressionAttributeNames = hashMapOf<String, String>()
  internal val expressionAttributeValues = hashMapOf<String, AttributeValue>()

  var set: Set? = null
  var returnValues: String = "ALL_NEW"

  inner class Set {
    internal val expression = StringBuilder("SET ")

    fun assign(vararg pairs: Pair<String, AttributeValue>) {
      pairs.forEach {
        expressionAttributeNames["#rep_${it.first}"] = it.first
        expressionAttributeValues[":rep_${it.first}"] = it.second

        if (expression.length > 4) {
          expression.append(" ,")
        }
        expression.append("#rep_").append(it.first).append(" = :rep_").append(it.first)
      }
    }

    fun ifNotExists(replace: String, with: Pair<String, AttributeValue>) {
      expressionAttributeNames["#nexr_${replace}"] = replace
      expressionAttributeNames["#nexv_${with.first}"] = with.first
      expressionAttributeValues["#nexv_${with.first}"] = with.second

      if (expression.length > 4) {
        expression.append(" ,")
      }
      expression.append("#nexr_").append(replace).append(" = if_not_exists(#nexv_").append(with.first).append(", :nexv_").append(with.first).append(")")
    }
  }

  inline fun set(fn: Set.() -> Unit) {
    set = Set().apply(fn)
  }

  fun returnUpdatedOld() {
    returnValues = "UPDATED_OLD"
  }

  fun returnUpdatedNew() {
    returnValues = "UPDATED_NEW"
  }

  fun returnAllOld() {
    returnValues = "ALL_OLD"
  }

  fun returnAllNew() {
    returnValues = "ALL_NEW"
  }

  fun key(hash: Pair<String, AttributeValue>, range: Pair<String, AttributeValue>? = null) {
    key[hash.first] = hash.second

    if (null != range) {
      key[range.first] = range.second
    }
  }

  fun toRequest() = UpdateItemRequest.builder()
    .tableName(table)
    .expressionAttributeNames(expressionAttributeNames)
    .expressionAttributeValues(expressionAttributeValues)
    .updateExpression(set?.expression.toString())
    .returnValues(returnValues)
    .build()
}

inline fun DynamoDbAsyncClient.updateItem(table: String, fn: UpdateItemDSL.() -> Unit) =
  updateItem(UpdateItemDSL(table).also { fn(it) }.toRequest())
