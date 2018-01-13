package com.databricks

import org.json4s._

/**
  * Information about a token
  * @param token_id - token hash (sha256) - unique identifier of the token
  * @param creation_time - value returned by System.currentTimeMillis() when the token
  *                      was generated on server side. It is the difference,
  *                      measured in milliseconds, between the current time and midnight,
  *                      January 1, 1970 UTC.
  * @param expiry_time - creation_time + 1000 * lifetimeInSec where lifetimeInSec is
  *                    a parameter passed into [[Token.create()]]
  * @param comment - a comment passed into [[Token.create()]]
  */
case class TokenInfo(token_id: String,
                     creation_time: Long,
                     expiry_time: Long,
                     comment: String
                    )

/**
  * A pair of (token value, [[TokenInfo]]) returned when new token is generated by
  * Databricks token store.
  *
  * @param token_value - a random 128-bit value that can be used for authentication
  *                    at Databricks REST services
  * @param token_info - token meta-info
  */
case class NewToken(token_value: String, token_info: TokenInfo)

/**
  * Helper class for extracting of a list of token from json
  * @param token_infos - tokens meta-info [[TokenInfo]]
  */
case class TokenList(token_infos: List[TokenInfo])

/**
  * Proxy class for Databricks Token API. An instance of the class is available
  * in any shard session. For instance, having of shardSession:
  * {{{
  *   // Creating of new token with life time 1 hour
  *   val newToken = shardSession.token.create(60*60, "dev token")
  *   // Getting of all access token
  *   val tokens = shardSession.token.list
  *   println(s"tokens = ${shardSession.token.list}")
  *   // Find staging tokens and delete them
  *   tokens.collect {
  *     case tokenInfo if tokenInfo.comment == "stage" => tokenInfo.token_id
  *   } foreach shardSession.token.delete
  * }}}
  * @param client - connection settings to user's shard
  */
class Token(client: ShardClient) extends Endpoint {
  private implicit val formats = DefaultFormats
  /** Common suffix of paths to token endpoints */
  override def path: String = client.path + "/2.0/token"

  /**
    * Creates new token in the shard if the feature was enabled by Databricks Admin:
    * https://docs.databricks.com/administration-guide/admin-settings/tokens.html#enabling-tokens
    *
    * @param lifetimeInSec - expiration time (in seconds) associated with the new token
    * @param comment - comment associated with the new token.
    * @return [[NewToken]] - a pair of token value + its meta-info [[TokenInfo]].
    *        The token value should be used for authentication at Databricks services.
    */
  def create(lifetimeInSec: Long, comment: String): NewToken = {
    val resp = client.req(s"$path/create", "post",
      s"""{"lifetime_seconds": ${lifetimeInSec},"comment": "${comment}"}"""
    )
    client.extract[NewToken](resp)
  }

  /**
    * Deletes (un-registered) a token by its id
    * @param token_id - token identifier returned in [[TokenInfo]]
    * @return true - if the token was removed successfully otherwise false
    */
  def delete(token_id: String): Unit = {
    val resp = client.req(s"$path/delete", "post",
      s"""{"token_id":"$token_id"}"""
    )
    client.extract[JObject](resp)
  }

  /**
    * Lists all active access token for given shard and user
    * @return a list of [[TokenInfo]]. It doesn't contain token value
    *         which could be used for authentication
    */
  def list: List[TokenInfo] = {
    val resp = client.req(s"$path/list", "get")
    client.extract[TokenList](resp).token_infos
  }
}